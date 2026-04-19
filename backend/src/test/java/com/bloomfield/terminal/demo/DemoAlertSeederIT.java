package com.bloomfield.terminal.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.alerts.domain.ThresholdOperator;
import com.bloomfield.terminal.alerts.internal.AlertRuleRepository;
import com.bloomfield.terminal.user.api.UserDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DemoAlertSeederIT extends AbstractDemoProfileIT {

  @Autowired UserDirectory userDirectory;
  @Autowired AlertRuleRepository alertRuleRepository;

  @Test
  void seedsThreeAlertRulesForAnalyst() {
    var analyst = userDirectory.findByEmail("analyst@demo.bloomfield").orElseThrow();
    var rules = alertRuleRepository.findByUserId(analyst.id());

    assertThat(rules).hasSize(3);
    assertThat(rules).extracting("ticker").containsExactlyInAnyOrder("BOAC", "PALC", "SNTS");
    assertThat(rules)
        .extracting("operator")
        .containsExactlyInAnyOrder(
            ThresholdOperator.ABOVE, ThresholdOperator.BELOW, ThresholdOperator.CROSSES_UP);
    assertThat(rules).allMatch(r -> r.enabled());
  }
}
