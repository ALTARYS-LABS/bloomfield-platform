package com.bloomfield.terminal.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.marketdata.api.OhlcvCandle;
import com.bloomfield.terminal.marketdata.internal.OhlcvRepository;
import com.bloomfield.terminal.user.AbstractPostgresIT;
import com.bloomfield.terminal.user.api.dto.LoginRequest;
import com.bloomfield.terminal.user.api.dto.RegisterRequest;
import com.bloomfield.terminal.user.api.dto.TokenResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Test d'intégration de bout en bout : insertion via {@link OhlcvRepository} dans l'hypertable
 * TimescaleDB, puis lecture authentifiée via {@code /api/brvm/candles/*} pour chaque granularité
 * (1m, 5m, 1h). S'appuie sur l'image {@code timescale/timescaledb:latest-pg17} configurée par
 * {@link AbstractPostgresIT}.
 */
class CandleEndpointIT extends AbstractPostgresIT {

  @Autowired TestRestTemplate rest;
  @Autowired OhlcvRepository repository;

  private HttpHeaders authHeaders;

  @BeforeEach
  void authenticate() {
    // /api/brvm/** exige un JWT : on enregistre puis connecte un utilisateur jetable.
    var email = "candles-" + UUID.randomUUID() + "@example.com";
    var password = "password123";
    rest.postForEntity(
        "/auth/register", new RegisterRequest(email, password, "Candles User"), Void.class);
    var login =
        rest.postForEntity("/auth/login", new LoginRequest(email, password), TokenResponse.class);
    authHeaders = new HttpHeaders();
    authHeaders.setBearerAuth(login.getBody().accessToken());
  }

  private ResponseEntity<List<OhlcvCandle>> getCandles(String path) {
    return rest.exchange(
        path,
        HttpMethod.GET,
        new HttpEntity<>(authHeaders),
        new ParameterizedTypeReference<List<OhlcvCandle>>() {});
  }

  @Test
  void oneMinuteIntervalReturnsInsertedBucketsInChronologicalOrder() {
    String ticker = "AGGR";
    Instant base = Instant.parse("2025-03-01T10:00:00Z");

    for (int i = 0; i < 5; i++) {
      BigDecimal price = new BigDecimal(100 + i);
      repository.upsert(
          ticker, base.plus(i, ChronoUnit.MINUTES), price, price, price, price, 10L + i);
    }

    var response =
        getCandles(
            "/api/brvm/candles/"
                + ticker
                + "?interval=1m&from="
                + base
                + "&to="
                + base.plus(10, ChronoUnit.MINUTES));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var candles = response.getBody();
    assertThat(candles).hasSize(5);
    assertThat(candles)
        .extracting(OhlcvCandle::close)
        .containsExactly(
            new BigDecimal("100.0000"),
            new BigDecimal("101.0000"),
            new BigDecimal("102.0000"),
            new BigDecimal("103.0000"),
            new BigDecimal("104.0000"));
    // Ordre chronologique ascendant (format lightweight-charts).
    assertThat(candles.get(0).time()).isLessThan(candles.get(4).time());
  }

  @Test
  void limitTrimsOldestWhenRangeExceedsLimit() {
    String ticker = "LIMIT";
    Instant base = Instant.parse("2025-03-01T11:00:00Z");

    for (int i = 0; i < 10; i++) {
      BigDecimal price = new BigDecimal(200 + i);
      repository.upsert(ticker, base.plus(i, ChronoUnit.MINUTES), price, price, price, price, 1L);
    }

    var response =
        getCandles(
            "/api/brvm/candles/"
                + ticker
                + "?interval=1m&from="
                + base
                + "&to="
                + base.plus(20, ChronoUnit.MINUTES)
                + "&limit=3");

    var candles = response.getBody();
    assertThat(candles).hasSize(3);
    // On garde les 3 plus récentes (close 207, 208, 209), triées en ordre ascendant.
    assertThat(candles)
        .extracting(OhlcvCandle::close)
        .containsExactly(
            new BigDecimal("207.0000"), new BigDecimal("208.0000"), new BigDecimal("209.0000"));
  }

  @Test
  void fiveMinuteIntervalBucketsRawMinutes() {
    String ticker = "FIVE";
    Instant base = Instant.parse("2025-03-01T12:00:00Z");

    // 10 bougies 1m : minutes 0..9. time_bucket('5 minutes') regroupe en 2 bougies 5m.
    for (int i = 0; i < 10; i++) {
      BigDecimal price = new BigDecimal(300 + i);
      repository.upsert(ticker, base.plus(i, ChronoUnit.MINUTES), price, price, price, price, 1L);
    }

    var response =
        getCandles(
            "/api/brvm/candles/"
                + ticker
                + "?interval=5m&from="
                + base
                + "&to="
                + base.plus(20, ChronoUnit.MINUTES));

    var candles = response.getBody();
    assertThat(candles).hasSize(2);
    // Premier bucket : minutes 0..4 → open=300, close=304 ; volume agrégé = 5.
    assertThat(candles.get(0).open()).isEqualByComparingTo("300");
    assertThat(candles.get(0).close()).isEqualByComparingTo("304");
    assertThat(candles.get(0).volume()).isEqualTo(5L);
  }

  @Test
  void hourlyContinuousAggregateServesOneHourInterval() {
    String ticker = "HOURLY";
    Instant base = Instant.parse("2025-03-01T13:00:00Z");

    for (int i = 0; i < 60; i++) {
      BigDecimal price = new BigDecimal(400 + i);
      repository.upsert(ticker, base.plus(i, ChronoUnit.MINUTES), price, price, price, price, 1L);
    }

    // Force le rafraîchissement de l'agrégat continu pour rendre les données visibles.
    repository.refreshHourlyAggregate(
        base.minus(1, ChronoUnit.HOURS), base.plus(2, ChronoUnit.HOURS));

    var response =
        getCandles(
            "/api/brvm/candles/"
                + ticker
                + "?interval=1h&from="
                + base.minus(1, ChronoUnit.HOURS)
                + "&to="
                + base.plus(2, ChronoUnit.HOURS));

    var candles = response.getBody();
    assertThat(candles).hasSize(1);
    assertThat(candles.get(0).open()).isEqualByComparingTo("400");
    assertThat(candles.get(0).close()).isEqualByComparingTo("459");
    assertThat(candles.get(0).volume()).isEqualTo(60L);
  }

  @Test
  void invalidIntervalReturnsBadRequest() {
    var response =
        rest.exchange(
            "/api/brvm/candles/SNTS?interval=2m",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
