package com.bloomfield.terminal.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.alerts.internal.AlertRuleRepository;
import com.bloomfield.terminal.portfolio.internal.PortfolioService;
import com.bloomfield.terminal.user.api.UserDirectory;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

/**
 * Vérifie que relancer les trois seeders démo ne crée pas de doublons : utilisateurs, positions,
 * règles d'alerte restent en quantités stables après une seconde exécution.
 */
class DemoSeederIdempotencyTest extends AbstractDemoProfileIT {

  @Autowired ApplicationContext applicationContext;
  @Autowired UserDirectory userDirectory;
  @Autowired PortfolioService portfolioService;
  @Autowired AlertRuleRepository alertRuleRepository;

  @Test
  void runningSeedersTwiceIsANoOp() throws Exception {
    var analyst = userDirectory.findByEmail("analyst@demo.bloomfield").orElseThrow();
    int positionsBefore = portfolioService.summaryFor(analyst.id()).positions().size();
    int rulesBefore = alertRuleRepository.findByUserId(analyst.id()).size();

    // On relance explicitement tous les ApplicationRunner du contexte (les seeders y sont inclus).
    Collection<ApplicationRunner> runners =
        applicationContext.getBeansOfType(ApplicationRunner.class).values();
    ApplicationArguments args = new DefaultApplicationArguments();
    for (ApplicationRunner r : runners) {
      r.run(args);
    }

    assertThat(userDirectory.findByEmail("admin@altaryslabs.com")).isPresent();
    assertThat(portfolioService.summaryFor(analyst.id()).positions()).hasSize(positionsBefore);
    assertThat(alertRuleRepository.findByUserId(analyst.id())).hasSize(rulesBefore);
  }
}
