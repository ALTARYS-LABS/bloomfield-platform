package com.bloomfield.terminal.alerts.internal;

import com.bloomfield.terminal.alerts.domain.AlertRule;
import com.bloomfield.terminal.alerts.domain.ThresholdOperator;
import com.bloomfield.terminal.user.api.UserDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seed idempotent de 3 règles d'alerte pour l'ANALYST. Dimensionnées pour qu'au moins une se
 * déclenche dans les ~10 premières minutes de la démo, avec une dérive aléatoire de ±0,3 % par tick
 * dans le simulateur de marché.
 *
 * <p>Ordre 3 : dépend du seed utilisateur (ordre 1).
 */
@Component
@Profile("demo")
@Order(3)
class DemoAlertSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoAlertSeeder.class);
  private static final String ANALYST_EMAIL = "analyst@demo.bloomfield";

  // Seuils très proches du prix d'ouverture : l'un des trois se déclenche quasi-certainement
  // dans les 10 premières minutes avec une dérive ±0,3% par tick (marche aléatoire).
  private static final List<DemoAlert> ALERTS =
      List.of(
          new DemoAlert("BOAC", ThresholdOperator.ABOVE, new BigDecimal("5215.0000")),
          new DemoAlert("PALC", ThresholdOperator.BELOW, new BigDecimal("4885.0000")),
          new DemoAlert("SNTS", ThresholdOperator.CROSSES_UP, new BigDecimal("18920.0000")));

  private final AlertRuleRepository alertRuleRepository;
  private final UserDirectory userDirectory;

  DemoAlertSeeder(AlertRuleRepository alertRuleRepository, UserDirectory userDirectory) {
    this.alertRuleRepository = alertRuleRepository;
    this.userDirectory = userDirectory;
  }

  @Override
  public void run(ApplicationArguments args) {
    var analyst = userDirectory.findByEmail(ANALYST_EMAIL).orElse(null);
    if (analyst == null) {
      log.warn("Demo analyst user not found, skipping alert seed: {}", ANALYST_EMAIL);
      return;
    }
    var existing = alertRuleRepository.findByUserId(analyst.id());
    for (DemoAlert a : ALERTS) {
      // Idempotence : on ne recrée pas une règle (ticker, operator, threshold) déjà présente.
      var already =
          existing.stream()
              .anyMatch(
                  r ->
                      r.ticker().equals(a.ticker())
                          && r.operator() == a.operator()
                          && r.threshold().compareTo(a.threshold()) == 0);
      if (already) {
        continue;
      }
      alertRuleRepository.save(
          AlertRule.newRule(
              UUID.randomUUID(),
              analyst.id(),
              a.ticker(),
              a.operator(),
              a.threshold(),
              true,
              Instant.now()));
      log.info(
          "Demo alert seeded: ticker={} operator={} threshold={}",
          a.ticker(),
          a.operator(),
          a.threshold());
    }
  }

  private record DemoAlert(String ticker, ThresholdOperator operator, BigDecimal threshold) {}
}
