package com.bloomfield.terminal.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.portfolio.internal.PortfolioService;
import com.bloomfield.terminal.user.api.UserDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DemoPortfolioSeederIT extends AbstractDemoProfileIT {

  @Autowired UserDirectory userDirectory;
  @Autowired PortfolioService portfolioService;

  @Test
  void seedsSixPositionsForAnalyst() {
    var analyst = userDirectory.findByEmail("analyst@demo.bloomfield").orElseThrow();
    var summary = portfolioService.summaryFor(analyst.id());

    assertThat(summary.positions()).hasSize(6);
    assertThat(summary.positions())
        .extracting("ticker")
        .containsExactlyInAnyOrder("SNTS", "BOAC", "SGBC", "ONTBF", "PALC", "SOGC");
  }
}
