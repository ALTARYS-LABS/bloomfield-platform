package com.bloomfield.terminal.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.marketdata.api.MarketDataProvider;
import com.bloomfield.terminal.portfolio.api.Side;
import com.bloomfield.terminal.portfolio.api.dto.PortfolioSummary;
import com.bloomfield.terminal.portfolio.api.dto.SubmitTradeRequest;
import com.bloomfield.terminal.portfolio.api.dto.TradeView;
import com.bloomfield.terminal.user.AbstractPostgresIT;
import com.bloomfield.terminal.user.api.dto.LoginRequest;
import com.bloomfield.terminal.user.api.dto.RegisterRequest;
import com.bloomfield.terminal.user.api.dto.TokenResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

// Parcours complet du module portefeuille : un utilisateur s'inscrit, se connecte, achète puis
// vend, reçoit un P&L réalisé, et ne voit pas le portefeuille d'un autre utilisateur.
class PortfolioControllerIT extends AbstractPostgresIT {

  @Autowired TestRestTemplate rest;
  @Autowired MarketDataProvider marketDataProvider;

  @Test
  void buyThenSell_updatesSummaryAndRecordsRealizedPnl() {
    // Pick a real BRVM ticker so MarketDataProvider returns a price.
    var ticker = marketDataProvider.currentQuotes().getFirst().ticker();
    var bearer = registerAndLogin();

    // Initial summary : aucun ticker en portefeuille.
    var initial =
        rest.exchange("/api/portfolio", HttpMethod.GET, bearer(bearer), PortfolioSummary.class);
    assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(initial.getBody()).isNotNull();
    assertThat(initial.getBody().positions()).isEmpty();

    // BUY 10 unités.
    var buy =
        rest.exchange(
            "/api/portfolio/trades",
            HttpMethod.POST,
            new HttpEntity<>(
                new SubmitTradeRequest(ticker, Side.BUY, new BigDecimal("10")), bearer.headers()),
            PortfolioSummary.class);
    assertThat(buy.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(buy.getBody().positions()).hasSize(1);
    assertThat(buy.getBody().positions().getFirst().ticker()).isEqualTo(ticker);
    assertThat(buy.getBody().positions().getFirst().quantity()).isEqualByComparingTo("10");

    // SELL 4 : le trade enregistre un P&L réalisé (potentiellement zéro si le prix n'a pas bougé).
    var sell =
        rest.exchange(
            "/api/portfolio/trades",
            HttpMethod.POST,
            new HttpEntity<>(
                new SubmitTradeRequest(ticker, Side.SELL, new BigDecimal("4")), bearer.headers()),
            PortfolioSummary.class);
    assertThat(sell.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(sell.getBody().positions().getFirst().quantity()).isEqualByComparingTo("6");

    // Historique : deux trades, le plus récent en tête.
    var trades =
        rest.exchange(
            "/api/portfolio/trades?limit=10",
            HttpMethod.GET,
            bearer(bearer),
            new ParameterizedTypeReference<List<TradeView>>() {});
    assertThat(trades.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(trades.getBody()).hasSize(2);
    assertThat(trades.getBody().getFirst().side()).isEqualTo(Side.SELL);
  }

  @Test
  void sellWithoutPosition_returns400() {
    var ticker = marketDataProvider.currentQuotes().getFirst().ticker();
    var bearer = registerAndLogin();
    var response =
        rest.exchange(
            "/api/portfolio/trades",
            HttpMethod.POST,
            new HttpEntity<>(
                new SubmitTradeRequest(ticker, Side.SELL, new BigDecimal("1")), bearer.headers()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void unknownTicker_returns400() {
    var bearer = registerAndLogin();
    var response =
        rest.exchange(
            "/api/portfolio/trades",
            HttpMethod.POST,
            new HttpEntity<>(
                new SubmitTradeRequest("ZZZZ", Side.BUY, new BigDecimal("1")), bearer.headers()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anonymous_cannotAccess() {
    var response = rest.getForEntity("/api/portfolio", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void portfolios_areIsolatedBetweenUsers() {
    var ticker = marketDataProvider.currentQuotes().getFirst().ticker();
    var alice = registerAndLogin();
    rest.exchange(
        "/api/portfolio/trades",
        HttpMethod.POST,
        new HttpEntity<>(
            new SubmitTradeRequest(ticker, Side.BUY, new BigDecimal("5")), alice.headers()),
        PortfolioSummary.class);

    var bob = registerAndLogin();
    var bobSummary =
        rest.exchange("/api/portfolio", HttpMethod.GET, bearer(bob), PortfolioSummary.class);
    // Bob voit son propre portefeuille (vide) et jamais celui d'Alice.
    assertThat(bobSummary.getBody().positions()).isEmpty();
    assertThat(bobSummary.getBody().id()).isNotEqualTo(alice.portfolioSummary().id());
  }

  private BearerContext registerAndLogin() {
    var email = "pf-" + UUID.randomUUID() + "@example.com";
    var password = "password123";
    rest.postForEntity(
        "/auth/register", new RegisterRequest(email, password, "PF User"), Void.class);
    var login =
        rest.postForEntity("/auth/login", new LoginRequest(email, password), TokenResponse.class);
    var headers = new HttpHeaders();
    headers.setBearerAuth(login.getBody().accessToken());
    var summary =
        rest.exchange(
                "/api/portfolio", HttpMethod.GET, new HttpEntity<>(headers), PortfolioSummary.class)
            .getBody();
    return new BearerContext(headers, summary);
  }

  private static HttpEntity<Void> bearer(BearerContext ctx) {
    return new HttpEntity<>(ctx.headers());
  }

  private record BearerContext(HttpHeaders headers, PortfolioSummary portfolioSummary) {}
}
