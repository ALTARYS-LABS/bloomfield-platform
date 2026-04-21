package com.bloomfield.terminal.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.user.AbstractPostgresIT;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Test d'intégration du seeder d'historique simulé. Vérifie deux critères d'acceptation :
 *
 * <ul>
 *   <li>le flag {@code seed-history-days=true} peuple l'hypertable pour tous les tickers seedés,
 *   <li>un second démarrage ne duplique pas les lignes (idempotent via {@code distinctDays}).
 * </ul>
 *
 * <p>Un seeder de count réduit ({@code seed-days=5}) est utilisé pour garder le test rapide ; la
 * logique métier est identique à la valeur par défaut de 30.
 */
class SimulatedHistorySeederIT extends AbstractPostgresIT {

  @DynamicPropertySource
  static void seederProps(DynamicPropertyRegistry registry) {
    registry.add("app.marketdata.simulated.seed-history-days", () -> "true");
    registry.add("app.marketdata.simulated.seed-days", () -> "5");
  }

  @Autowired SimulatedHistorySeeder seeder;
  @Autowired OhlcvRepository repository;
  @Autowired TickerSeedLoader seedLoader;

  @Test
  void seedsAllTickersAndIsIdempotentOnRerun() {
    String sampleTicker = seedLoader.seeds().getFirst().ticker();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Instant windowStart = today.minusDays(60).atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant windowEnd = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

    // Le seeder s'est déjà exécuté au démarrage du contexte Spring (ApplicationReadyEvent).
    Set<LocalDate> afterStartup = repository.distinctDays(sampleTicker, windowStart, windowEnd);
    assertThat(afterStartup).hasSize(5);

    // Second passage manuel : doit être un no-op net (distinctDays inchangé).
    seeder.seedOnStartup();
    Set<LocalDate> afterRerun = repository.distinctDays(sampleTicker, windowStart, windowEnd);
    assertThat(afterRerun).isEqualTo(afterStartup);
  }
}
