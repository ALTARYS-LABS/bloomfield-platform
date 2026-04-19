package com.bloomfield.terminal.marketdata.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Teste unitairement l'adaptateur HTTP bas-niveau. Vérifie la couche anti-corruption (ticker BRVM →
 * Sikafinance, date {@code DD/MM/YYYY}, prix entiers → BigDecimal scalé à 2), la stratégie de retry
 * (une fois sur 5xx, pas sur 4xx) et la forme exacte de la requête amont.
 */
class SikafinanceClientTest {

  private static WireMockServer wireMock;
  private SikafinanceClient client;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @BeforeEach
  void setup() {
    wireMock.resetAll();
    // HTTP/1.1 forcé : WireMock ne supporte pas l'upgrade h2c et le client JDK par défaut
    // tente HTTP/2 en clair, ce qui déclenche un RST_STREAM côté serveur.
    HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    RestClient rest =
        RestClient.builder()
            .baseUrl("http://localhost:" + wireMock.port())
            .defaultHeader("User-Agent", "test-agent")
            .defaultHeader("Origin", "https://www.sikafinance.com")
            .requestFactory(new JdkClientHttpRequestFactory(http))
            .build();
    client = new SikafinanceClient(rest);
  }

  @Test
  void requestUsesUpstreamTickerAndIsoDatesWithXperiodZero() {
    wireMock.stubFor(post(urlEqualTo("/api/general/GetHistos")).willReturn(okJson("{\"lst\":[]}")));

    client.fetchDaily("SGBC", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

    wireMock.verify(
        postRequestedFor(urlEqualTo("/api/general/GetHistos"))
            .withHeader("Referer", equalTo("https://www.sikafinance.com/marches/historiques/SGBCI"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "ticker": "SGBCI",
                      "datedeb": "2026-01-01",
                      "datefin": "2026-03-31",
                      "xperiod": "0"
                    }
                    """)));
  }

  @Test
  void parsesDdMmYyyyDatesToSessionCloseInstantAndScalesPrices() {
    wireMock.stubFor(
        post(urlEqualTo("/api/general/GetHistos"))
            .willReturn(
                okJson(
                    """
                    {"lst":[
                      {"Date":"15/01/2026","Open":12500,"High":12650,"Low":12480,"Close":12600,"Volume":1250}
                    ]}
                    """)));

    var bars = client.fetchDaily("SGBC", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

    assertThat(bars).hasSize(1);
    var bar = bars.get(0);
    // Ticker canonique préservé (pas de fuite de "SGBCI").
    assertThat(bar.ticker()).isEqualTo("SGBC");
    // 15:30 clôture BRVM, UTC = Africa/Abidjan (UTC+0).
    assertThat(bar.bucket()).isEqualTo(Instant.parse("2026-01-15T15:30:00Z"));
    assertThat(bar.open()).isEqualByComparingTo("12500.00");
    assertThat(bar.high()).isEqualByComparingTo("12650.00");
    assertThat(bar.low()).isEqualByComparingTo("12480.00");
    assertThat(bar.close()).isEqualByComparingTo("12600.00");
    assertThat(bar.open().scale()).isEqualTo(2);
    assertThat(bar.volume()).isEqualTo(1250L);
  }

  @Test
  void emptyLstReturnsEmptyList() {
    wireMock.stubFor(post(urlEqualTo("/api/general/GetHistos")).willReturn(okJson("{\"lst\":[]}")));

    assertThat(client.fetchDaily("SNTS", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)))
        .isEmpty();
  }

  @Test
  void fiveHundredErrorIsRetriedExactlyOnceThenSucceeds() {
    String scenario = "retry-5xx";
    wireMock.stubFor(
        post(urlEqualTo("/api/general/GetHistos"))
            .inScenario(scenario)
            .whenScenarioStateIs("Started")
            .willReturn(serverError())
            .willSetStateTo("recovered"));
    wireMock.stubFor(
        post(urlEqualTo("/api/general/GetHistos"))
            .inScenario(scenario)
            .whenScenarioStateIs("recovered")
            .willReturn(okJson("{\"lst\":[]}")));

    var bars = client.fetchDaily("SNTS", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9));

    assertThat(bars).isEmpty();
    wireMock.verify(2, postRequestedFor(urlEqualTo("/api/general/GetHistos")));
  }

  @Test
  void fourHundredIsNotRetried() {
    wireMock.stubFor(post(urlEqualTo("/api/general/GetHistos")).willReturn(status(404)));

    assertThatThrownBy(
            () -> client.fetchDaily("XXXX", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)))
        .isInstanceOf(HttpClientErrorException.class);
    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/general/GetHistos")));
  }

  @Test
  void unknownTickerFallsBackToSameCode() {
    wireMock.stubFor(post(urlEqualTo("/api/general/GetHistos")).willReturn(okJson("{\"lst\":[]}")));

    client.fetchDaily("SNTS", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9));

    wireMock.verify(
        postRequestedFor(urlEqualTo("/api/general/GetHistos"))
            .withRequestBody(matchingJsonPath("$.ticker", equalTo("SNTS"))));
  }
}
