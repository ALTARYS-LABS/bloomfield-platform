package com.bloomfield.terminal.portfolio.domain;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

// Une position agrège quantité et coût moyen pour un (portefeuille, ticker). L'unicité
// (portfolio_id, ticker) est garantie par un index SQL : côté Java on passe par un upsert
// logique dans PortfolioService (findByPortfolioIdAndTicker puis update ou insert).
@Table("positions")
public final class Position implements Persistable<UUID> {

  @Id private final UUID id;

  @Column("portfolio_id")
  private final UUID portfolioId;

  private final String ticker;
  private final BigDecimal quantity;

  @Column("avg_cost")
  private final BigDecimal avgCost;

  @Transient private final boolean isNew;

  @PersistenceCreator
  public Position(
      UUID id, UUID portfolioId, String ticker, BigDecimal quantity, BigDecimal avgCost) {
    this(id, portfolioId, ticker, quantity, avgCost, false);
  }

  private Position(
      UUID id,
      UUID portfolioId,
      String ticker,
      BigDecimal quantity,
      BigDecimal avgCost,
      boolean isNew) {
    this.id = id;
    this.portfolioId = portfolioId;
    this.ticker = ticker;
    this.quantity = quantity;
    this.avgCost = avgCost;
    this.isNew = isNew;
  }

  public static Position newPosition(
      UUID id, UUID portfolioId, String ticker, BigDecimal quantity, BigDecimal avgCost) {
    return new Position(id, portfolioId, ticker, quantity, avgCost, true);
  }

  public Position withQuantityAndAvgCost(BigDecimal nextQuantity, BigDecimal nextAvgCost) {
    // Recréation immuable : on conserve l'ID existant et on repasse en mode "update".
    return new Position(id, portfolioId, ticker, nextQuantity, nextAvgCost, false);
  }

  public UUID id() {
    return id;
  }

  public UUID portfolioId() {
    return portfolioId;
  }

  public String ticker() {
    return ticker;
  }

  public BigDecimal quantity() {
    return quantity;
  }

  public BigDecimal avgCost() {
    return avgCost;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }
}
