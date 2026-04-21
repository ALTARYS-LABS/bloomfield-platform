package com.bloomfield.terminal.portfolio.internal;

import com.bloomfield.terminal.portfolio.domain.Portfolio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

interface PortfolioRepository extends CrudRepository<Portfolio, UUID> {
  Optional<Portfolio> findByUserId(UUID userId);
}
