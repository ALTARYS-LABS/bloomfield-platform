package com.bloomfield.terminal.portfolio.internal;

import com.bloomfield.terminal.portfolio.api.dto.PortfolioSummary;
import com.bloomfield.terminal.portfolio.api.dto.PositionView;
import com.bloomfield.terminal.portfolio.domain.Position;
import com.bloomfield.terminal.portfolio.domain.Trade;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

// Calculs P&L : fonction pure, pas de dépendance Spring, 100 % testable unitaire.
// - Unrealized P&L position = quantity * (currentPrice - avgCost)
// - Market value position   = quantity * currentPrice
// - Total cost              = somme des quantity * avgCost
// - Realized P&L portfolio  = somme des trades.realized_pnl (FIFO au moment du trade)
final class PnlCalculator {

  // Suffisamment de chiffres pour éviter la perte de précision sur une division de BigDecimal
  // tout en gardant un coût raisonnable. Les arrondis finaux sont faits à la serialisation.
  private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

  private PnlCalculator() {}

  static PortfolioSummary summarise(
      UUID portfolioId,
      String portfolioName,
      List<Position> positions,
      List<Trade> trades,
      Function<String, BigDecimal> currentPriceOf) {

    var views =
        positions.stream().map(p -> positionView(p, currentPriceOf.apply(p.ticker()))).toList();

    var totalValue =
        views.stream().map(PositionView::marketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    var totalCost =
        positions.stream()
            .map(p -> p.quantity().multiply(p.avgCost()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var unrealized = totalValue.subtract(totalCost);
    var realized =
        trades.stream()
            .map(Trade::realizedPnl)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var unrealizedPct = percent(unrealized, totalCost);

    return new PortfolioSummary(
        portfolioId,
        portfolioName,
        views,
        totalValue,
        totalCost,
        unrealized,
        realized,
        unrealizedPct);
  }

  static PositionView positionView(Position p, BigDecimal currentPrice) {
    // currentPrice absent (ticker non trouvé côté marketdata) : on retombe sur avgCost pour
    // présenter un P&L nul plutôt que d'exploser. C'est un choix de robustesse prototype.
    var price = currentPrice != null ? currentPrice : p.avgCost();
    var marketValue = p.quantity().multiply(price);
    var costBasis = p.quantity().multiply(p.avgCost());
    var pnl = marketValue.subtract(costBasis);
    var pnlPct = percent(pnl, costBasis);
    return new PositionView(p.ticker(), p.quantity(), p.avgCost(), price, marketValue, pnl, pnlPct);
  }

  // Calcul du nouveau coût moyen pondéré lors d'un achat additionnel.
  // avgCost' = (oldQty * oldAvg + addQty * tradePrice) / (oldQty + addQty)
  static BigDecimal weightedAverageCost(
      BigDecimal oldQuantity,
      BigDecimal oldAvgCost,
      BigDecimal addQuantity,
      BigDecimal tradePrice) {
    var totalQuantity = oldQuantity.add(addQuantity);
    if (totalQuantity.signum() == 0) {
      return BigDecimal.ZERO;
    }
    var totalCost = oldQuantity.multiply(oldAvgCost).add(addQuantity.multiply(tradePrice));
    return totalCost.divide(totalQuantity, MC);
  }

  // P&L réalisé d'une vente FIFO naïve : (prix de vente - coût moyen courant) * quantité vendue.
  // Ce modèle est volontairement simple pour le prototype ; un FIFO lot-par-lot viendrait plus
  // tard.
  static BigDecimal realizedOnSell(BigDecimal avgCost, BigDecimal tradePrice, BigDecimal quantity) {
    return tradePrice.subtract(avgCost).multiply(quantity);
  }

  private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
    if (denominator == null || denominator.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return numerator.divide(denominator, MC).multiply(BigDecimal.valueOf(100));
  }
}
