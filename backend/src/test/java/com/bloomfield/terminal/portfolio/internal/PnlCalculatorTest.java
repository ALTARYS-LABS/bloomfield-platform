package com.bloomfield.terminal.portfolio.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.portfolio.api.Side;
import com.bloomfield.terminal.portfolio.domain.Position;
import com.bloomfield.terminal.portfolio.domain.Trade;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

// Test unitaire du moteur de calcul P&L. Placé dans le même package pour un accès direct aux
// méthodes package-private, sans réflexion.
class PnlCalculatorTest {

  @Test
  void weightedAverageCost_combinesLotsProportionally() {
    // 10 @ 1000 puis 5 @ 2000 -> coût moyen pondéré = 20000 / 15.
    var result = PnlCalculator.weightedAverageCost(bd("10"), bd("1000"), bd("5"), bd("2000"));
    assertThat(result).isEqualByComparingTo(bd("1333.3333333333333333"));
  }

  @Test
  void weightedAverageCost_returnsZeroWhenTotalIsZero() {
    var result = PnlCalculator.weightedAverageCost(bd("0"), bd("0"), bd("0"), bd("0"));
    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void realizedOnSell_computesGain() {
    // Vente de 3 unités à 1500 avec coût moyen 1000 -> (1500-1000)*3 = 1500.
    var gain = PnlCalculator.realizedOnSell(bd("1000"), bd("1500"), bd("3"));
    assertThat(gain).isEqualByComparingTo(bd("1500"));
  }

  @Test
  void realizedOnSell_computesLoss() {
    // Vente à perte : coût 2000, prix 1200, quantité 2 -> -1600.
    var loss = PnlCalculator.realizedOnSell(bd("2000"), bd("1200"), bd("2"));
    assertThat(loss).isEqualByComparingTo(bd("-1600"));
  }

  @Test
  void summarise_aggregatesPositionsAndTrades() {
    var portfolioId = UUID.randomUUID();
    var positions =
        List.of(
            Position.newPosition(UUID.randomUUID(), portfolioId, "SNTS", bd("10"), bd("1000")),
            Position.newPosition(UUID.randomUUID(), portfolioId, "BICC", bd("5"), bd("2000")));
    var trades =
        List.of(
            Trade.newTrade(
                UUID.randomUUID(),
                portfolioId,
                "SNTS",
                Side.SELL,
                bd("2"),
                bd("1200"),
                OffsetDateTime.now(),
                bd("400")),
            Trade.newTrade(
                UUID.randomUUID(),
                portfolioId,
                "BICC",
                Side.BUY,
                bd("5"),
                bd("2000"),
                OffsetDateTime.now(),
                null));
    Map<String, BigDecimal> prices = Map.of("SNTS", bd("1100"), "BICC", bd("2500"));
    Function<String, BigDecimal> priceOf = prices::get;

    var summary = PnlCalculator.summarise(portfolioId, "Principal", positions, trades, priceOf);

    // totalValue = 10*1100 + 5*2500 = 23500
    assertThat(summary.totalValue()).isEqualByComparingTo(bd("23500"));
    // totalCost = 10*1000 + 5*2000 = 20000
    assertThat(summary.totalCost()).isEqualByComparingTo(bd("20000"));
    assertThat(summary.unrealizedPnl()).isEqualByComparingTo(bd("3500"));
    assertThat(summary.realizedPnl()).isEqualByComparingTo(bd("400"));
    assertThat(summary.positions()).hasSize(2);
  }

  @Test
  void summarise_fallsBackToAvgCostWhenPriceIsMissing() {
    // Ticker sans prix marché courant : le P&L non réalisé doit rester neutre.
    var portfolioId = UUID.randomUUID();
    var positions =
        List.of(Position.newPosition(UUID.randomUUID(), portfolioId, "XXX", bd("4"), bd("500")));
    var summary =
        PnlCalculator.summarise(portfolioId, "Principal", positions, List.of(), ticker -> null);

    assertThat(summary.totalValue()).isEqualByComparingTo(bd("2000"));
    assertThat(summary.totalCost()).isEqualByComparingTo(bd("2000"));
    assertThat(summary.unrealizedPnl()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.unrealizedPnlPercent()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void summarise_emptyPortfolio_returnsZeroTotals() {
    var portfolioId = UUID.randomUUID();
    var summary =
        PnlCalculator.summarise(portfolioId, "Vide", List.of(), List.of(), ticker -> bd("100"));
    assertThat(summary.totalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.totalCost()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.unrealizedPnl()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.realizedPnl()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.positions()).isEmpty();
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
