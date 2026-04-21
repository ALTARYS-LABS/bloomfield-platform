package com.bloomfield.terminal.portfolio.internal;

import com.bloomfield.terminal.portfolio.domain.Position;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

interface PositionRepository extends CrudRepository<Position, UUID> {
  List<Position> findAllByPortfolioId(UUID portfolioId);

  Optional<Position> findByPortfolioIdAndTicker(UUID portfolioId, String ticker);

  void deleteByPortfolioIdAndTicker(UUID portfolioId, String ticker);
}
