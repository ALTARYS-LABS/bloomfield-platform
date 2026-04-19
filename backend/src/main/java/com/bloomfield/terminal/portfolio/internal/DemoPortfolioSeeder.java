package com.bloomfield.terminal.portfolio.internal;

import com.bloomfield.terminal.portfolio.domain.Portfolio;
import com.bloomfield.terminal.portfolio.domain.Position;
import com.bloomfield.terminal.user.api.UserDirectory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * Seed idempotent du portefeuille de démo pour l'ANALYST. Pose 6 positions représentatives
 * (Finance, Télécoms, Agriculture) avec des coûts moyens cohérents avec les prix d'ouverture BRVM
 * utilisés par le simulateur de marché.
 *
 * <p>Ordre 2 : dépend du seed des utilisateurs (ordre 1) pour résoudre l'email ANALYST.
 */
@Component
@Profile("demo")
@Order(2)
class DemoPortfolioSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoPortfolioSeeder.class);
  private static final String ANALYST_EMAIL = "analyst@demo.bloomfield";
  private static final String PORTFOLIO_NAME = "Portefeuille démo ANALYST";

  // Positions figées pour la démo. Les avgCost sont proches des openPrice pour que le P&L
  // initial soit modeste (quelques %) et que la démo montre autant de gagnants que de perdants.
  private static final List<DemoPosition> POSITIONS =
      List.of(
          new DemoPosition("SNTS", new BigDecimal("40"), new BigDecimal("18500.0000")),
          new DemoPosition("BOAC", new BigDecimal("200"), new BigDecimal("5100.0000")),
          new DemoPosition("SGBC", new BigDecimal("50"), new BigDecimal("12300.0000")),
          new DemoPosition("ONTBF", new BigDecimal("150"), new BigDecimal("4250.0000")),
          new DemoPosition("PALC", new BigDecimal("100"), new BigDecimal("4950.0000")),
          new DemoPosition("SOGC", new BigDecimal("120"), new BigDecimal("4150.0000")));

  private final PortfolioRepository portfolioRepository;
  private final PositionRepository positionRepository;
  private final UserDirectory userDirectory;

  DemoPortfolioSeeder(
      PortfolioRepository portfolioRepository,
      PositionRepository positionRepository,
      UserDirectory userDirectory) {
    this.portfolioRepository = portfolioRepository;
    this.positionRepository = positionRepository;
    this.userDirectory = userDirectory;
  }

  @Override
  public void run(ApplicationArguments args) {
    var analyst = userDirectory.findByEmail(ANALYST_EMAIL).orElse(null);
    if (analyst == null) {
      log.warn("Demo analyst user not found, skipping portfolio seed: {}", ANALYST_EMAIL);
      return;
    }
    var portfolio =
        portfolioRepository
            .findByUserId(analyst.id())
            .orElseGet(
                () ->
                    portfolioRepository.save(
                        Portfolio.newPortfolio(
                            UUID.randomUUID(),
                            analyst.id(),
                            PORTFOLIO_NAME,
                            OffsetDateTime.now(ZoneOffset.UTC))));

    for (DemoPosition p : POSITIONS) {
      // Idempotence : si la position existe déjà pour ce (portefeuille, ticker) on ne la remplace
      // pas. Cela évite d'écraser un trade saisi à la main en cours de démo.
      var existing = positionRepository.findByPortfolioIdAndTicker(portfolio.id(), p.ticker());
      if (existing.isPresent()) {
        continue;
      }
      positionRepository.save(
          Position.newPosition(
              UUID.randomUUID(), portfolio.id(), p.ticker(), p.quantity(), p.avgCost()));
      log.info(
          "Demo position seeded: portfolio={} ticker={} qty={} avgCost={}",
          portfolio.id(),
          p.ticker(),
          p.quantity(),
          p.avgCost());
    }
  }

  private record DemoPosition(String ticker, BigDecimal quantity, BigDecimal avgCost) {}
}
