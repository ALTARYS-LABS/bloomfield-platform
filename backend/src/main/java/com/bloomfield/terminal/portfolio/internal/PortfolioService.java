package com.bloomfield.terminal.portfolio.internal;

import com.bloomfield.terminal.marketdata.api.MarketDataProvider;
import com.bloomfield.terminal.portfolio.api.Side;
import com.bloomfield.terminal.portfolio.api.dto.PortfolioSummary;
import com.bloomfield.terminal.portfolio.api.dto.SubmitTradeRequest;
import com.bloomfield.terminal.portfolio.api.dto.TradeView;
import com.bloomfield.terminal.portfolio.domain.Portfolio;
import com.bloomfield.terminal.portfolio.domain.Position;
import com.bloomfield.terminal.portfolio.domain.Trade;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Service applicatif du module portefeuille. Orchestration uniquement : toute la logique
// financière vit dans PnlCalculator, ce qui permet de la tester sans Spring.
@Service
@Transactional
public class PortfolioService {

  private final PortfolioRepository portfolioRepository;
  private final PositionRepository positionRepository;
  private final TradeRepository tradeRepository;
  private final MarketDataProvider marketDataProvider;
  private final Clock clock;

  PortfolioService(
      PortfolioRepository portfolioRepository,
      PositionRepository positionRepository,
      TradeRepository tradeRepository,
      MarketDataProvider marketDataProvider,
      Clock clock) {
    this.portfolioRepository = portfolioRepository;
    this.positionRepository = positionRepository;
    this.tradeRepository = tradeRepository;
    this.marketDataProvider = marketDataProvider;
    this.clock = clock;
  }

  // Récupère ou crée (à la volée) le portefeuille de l'utilisateur. Un utilisateur n'a qu'un
  // seul portefeuille dans le prototype : l'index unique idx_portfolios_user le garantit en base.
  public PortfolioSummary summaryFor(UUID userId) {
    var portfolio =
        portfolioRepository.findByUserId(userId).orElseGet(() -> createDefaultPortfolio(userId));
    return computeSummary(portfolio);
  }

  public List<TradeView> recentTrades(UUID userId, int limit) {
    var portfolio = portfolioRepository.findByUserId(userId).orElse(null);
    if (portfolio == null) {
      return List.of();
    }
    return tradeRepository
        .findAllByPortfolioIdOrderByExecutedAtDesc(portfolio.id(), Limit.of(limit))
        .stream()
        .map(PortfolioService::toView)
        .toList();
  }

  public PortfolioSummary submitTrade(UUID userId, SubmitTradeRequest request) {
    var portfolio =
        portfolioRepository.findByUserId(userId).orElseGet(() -> createDefaultPortfolio(userId));
    // Prix d'exécution = prix serveur courant, sinon on refuse.
    var price =
        marketDataProvider
            .tickerState(request.ticker())
            .map(state -> state.price())
            .orElseThrow(() -> new UnknownTickerException(request.ticker()));

    applyTrade(portfolio.id(), request.ticker(), request.side(), request.quantity(), price);
    return computeSummary(portfolio);
  }

  private void applyTrade(
      UUID portfolioId, String ticker, Side side, BigDecimal quantity, BigDecimal price) {
    var existing = positionRepository.findByPortfolioIdAndTicker(portfolioId, ticker).orElse(null);
    var now = OffsetDateTime.now(clock);
    BigDecimal realizedPnl = null;

    if (side == Side.BUY) {
      if (existing == null) {
        positionRepository.save(
            Position.newPosition(UUID.randomUUID(), portfolioId, ticker, quantity, price));
      } else {
        var newAvg =
            PnlCalculator.weightedAverageCost(
                existing.quantity(), existing.avgCost(), quantity, price);
        positionRepository.save(
            existing.withQuantityAndAvgCost(existing.quantity().add(quantity), newAvg));
      }
    } else {
      // SELL : il faut une position existante avec quantité suffisante, sinon on refuse.
      if (existing == null || existing.quantity().compareTo(quantity) < 0) {
        throw new InsufficientPositionException(ticker);
      }
      realizedPnl = PnlCalculator.realizedOnSell(existing.avgCost(), price, quantity);
      var remaining = existing.quantity().subtract(quantity);
      if (remaining.signum() == 0) {
        // Vente totale : on purge la position pour ne pas laisser de lignes à quantité nulle.
        positionRepository.deleteByPortfolioIdAndTicker(portfolioId, ticker);
      } else {
        positionRepository.save(existing.withQuantityAndAvgCost(remaining, existing.avgCost()));
      }
    }

    tradeRepository.save(
        Trade.newTrade(
            UUID.randomUUID(), portfolioId, ticker, side, quantity, price, now, realizedPnl));
  }

  private PortfolioSummary computeSummary(Portfolio portfolio) {
    var positions = positionRepository.findAllByPortfolioId(portfolio.id());
    var trades = tradeRepository.findAllByPortfolioId(portfolio.id());
    return PnlCalculator.summarise(
        portfolio.id(),
        portfolio.name(),
        positions,
        trades,
        ticker -> marketDataProvider.tickerState(ticker).map(state -> state.price()).orElse(null));
  }

  private Portfolio createDefaultPortfolio(UUID userId) {
    return portfolioRepository.save(
        Portfolio.newPortfolio(
            UUID.randomUUID(), userId, "Portefeuille principal", OffsetDateTime.now(clock)));
  }

  private static TradeView toView(Trade t) {
    return new TradeView(
        t.id(), t.ticker(), t.side(), t.quantity(), t.price(), t.executedAt(), t.realizedPnl());
  }
}
