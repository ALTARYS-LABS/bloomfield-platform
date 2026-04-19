package com.bloomfield.terminal.portfolio.internal;

import com.bloomfield.terminal.portfolio.domain.Trade;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.CrudRepository;

interface TradeRepository extends CrudRepository<Trade, UUID> {
  List<Trade> findAllByPortfolioIdOrderByExecutedAtDesc(UUID portfolioId, Limit limit);

  List<Trade> findAllByPortfolioId(UUID portfolioId);
}
