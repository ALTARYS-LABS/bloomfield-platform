package com.bloomfield.terminal.marketdata.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.user.AbstractPostgresIT;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Test d'intégration du loader historique : WireMock simule l'upstream Sikafinance, Testcontainers
 * PostgreSQL+TimescaleDB (via {@link AbstractPostgresIT}) fournit l'hypertable {@code ohlcv}.
 * Vérifie le critère d'acceptation central : premier appel = 1 requête amont + DB peuplée, second
 * appel identique = 0 requête.
 */
class HistoricalCandleLoaderIT extends AbstractPostgresIT {

  private static final WireMockServer WIRE_MOCK;

  static {
    WIRE_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    WIRE_MOCK.start();
  }

  @DynamicPropertySource
  static void sikafinanceProps(DynamicPropertyRegistry registry) {
    registry.add("app.marketdata.history-source", () -> "sikafinance");
    registry.add(
        "app.marketdata.sikafinance.base-url", () -> "http://localhost:" + WIRE_MOCK.port());
    // Espacement volontairement mesurable pour le test de spacing.
    registry.add("app.marketdata.sikafinance.request-spacing-ms", () -> "200");
  }

  @Autowired HistoricalCandleLoader loader;
  @Autowired OhlcvRepository repository;

  @BeforeEach
  void resetWireMock() {
    WIRE_MOCK.resetAll();
  }

  @Test
  void firstCallHitsUpstreamAndSecondCallIsFullyCached() {
    // Fenêtre Lundi 2026-01-05 → Vendredi 2026-01-09 : 5 jours ouvrés.
    WIRE_MOCK.stubFor(
        post(urlEqualTo("/api/general/GetHistos"))
            .willReturn(
                okJson(
                    """
                    {"lst":[
                      {"Date":"05/01/2026","Open":12500,"High":12550,"Low":12480,"Close":12520,"Volume":100},
                      {"Date":"06/01/2026","Open":12520,"High":12600,"Low":12510,"Close":12580,"Volume":110},
                      {"Date":"07/01/2026","Open":12580,"High":12700,"Low":12560,"Close":12690,"Volume":120},
                      {"Date":"08/01/2026","Open":12690,"High":12720,"Low":12640,"Close":12650,"Volume":130},
                      {"Date":"09/01/2026","Open":12650,"High":12680,"Low":12600,"Close":12670,"Volume":140}
                    ]}
                    """)));

    String ticker = "CACHE1";
    Instant from = Instant.parse("2026-01-05T00:00:00Z");
    Instant to = Instant.parse("2026-01-09T23:59:59Z");

    // Premier appel : 1 requête amont, 5 bougies écrites.
    loader.ensureCached(ticker, from, to);
    WIRE_MOCK.verify(1, postRequestedFor(urlEqualTo("/api/general/GetHistos")));
    assertThat(repository.distinctDays(ticker, from, to)).hasSize(5);

    // Échantillon : la bougie 05/01/2026 est bien scalée à 2 décimales et bucketisée à 15:30 UTC.
    Instant expectedBucket =
        LocalDate.of(2026, 1, 5).atTime(LocalTime.of(15, 30)).toInstant(ZoneOffset.UTC);
    assertThat(repository.latestBucket(ticker)).isPresent();
    assertThat(repository.distinctDays(ticker, expectedBucket, expectedBucket))
        .containsExactly(LocalDate.of(2026, 1, 5));

    // Second appel identique : zéro requête amont.
    WIRE_MOCK.resetRequests();
    loader.ensureCached(ticker, from, to);
    WIRE_MOCK.verify(0, postRequestedFor(urlEqualTo("/api/general/GetHistos")));
  }

  @Test
  void existingDayInMiddleSplitsIntoTwoRequestsWithSpacing() {
    // Le mercredi 2026-01-07 est déjà en base, le resolver doit produire 2 plages manquantes.
    Instant wednesday =
        LocalDate.of(2026, 1, 7).atTime(LocalTime.of(15, 30)).toInstant(ZoneOffset.UTC);
    repository.upsert(
        "SPLIT",
        wednesday,
        new BigDecimal("100.00"),
        new BigDecimal("100.00"),
        new BigDecimal("100.00"),
        new BigDecimal("100.00"),
        1L);

    WIRE_MOCK.stubFor(
        post(urlEqualTo("/api/general/GetHistos")).willReturn(okJson("{\"lst\":[]}")));

    Instant from = Instant.parse("2026-01-05T00:00:00Z");
    Instant to = Instant.parse("2026-01-09T23:59:59Z");

    long startNs = System.nanoTime();
    loader.ensureCached("SPLIT", from, to);
    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

    // Deux plages manquantes → deux requêtes amont, espacées d'au moins 200ms.
    WIRE_MOCK.verify(2, postRequestedFor(urlEqualTo("/api/general/GetHistos")));
    assertThat(elapsedMs).isGreaterThanOrEqualTo(200L);
  }
}
