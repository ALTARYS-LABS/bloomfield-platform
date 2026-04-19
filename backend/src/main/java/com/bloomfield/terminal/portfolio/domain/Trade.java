package com.bloomfield.terminal.portfolio.domain;

import com.bloomfield.terminal.portfolio.api.Side;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

// Un trade est immuable : il enregistre un achat ou une vente exécuté. Le P&L réalisé
// est calculé au moment de l'exécution (FIFO naïf sur la position existante) et stocké
// tel quel pour éviter de le recalculer à chaque lecture.
@Table("trades")
public final class Trade implements Persistable<UUID> {

  @Id private final UUID id;

  @Column("portfolio_id")
  private final UUID portfolioId;

  private final String ticker;
  private final Side side;
  private final BigDecimal quantity;
  private final BigDecimal price;

  @Column("executed_at")
  private final OffsetDateTime executedAt;

  @Column("realized_pnl")
  private final BigDecimal realizedPnl;

  @Transient private final boolean isNew;

  @PersistenceCreator
  public Trade(
      UUID id,
      UUID portfolioId,
      String ticker,
      Side side,
      BigDecimal quantity,
      BigDecimal price,
      OffsetDateTime executedAt,
      BigDecimal realizedPnl) {
    this(id, portfolioId, ticker, side, quantity, price, executedAt, realizedPnl, false);
  }

  private Trade(
      UUID id,
      UUID portfolioId,
      String ticker,
      Side side,
      BigDecimal quantity,
      BigDecimal price,
      OffsetDateTime executedAt,
      BigDecimal realizedPnl,
      boolean isNew) {
    this.id = id;
    this.portfolioId = portfolioId;
    this.ticker = ticker;
    this.side = side;
    this.quantity = quantity;
    this.price = price;
    this.executedAt = executedAt;
    this.realizedPnl = realizedPnl;
    this.isNew = isNew;
  }

  public static Trade newTrade(
      UUID id,
      UUID portfolioId,
      String ticker,
      Side side,
      BigDecimal quantity,
      BigDecimal price,
      OffsetDateTime executedAt,
      BigDecimal realizedPnl) {
    return new Trade(id, portfolioId, ticker, side, quantity, price, executedAt, realizedPnl, true);
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

  public Side side() {
    return side;
  }

  public BigDecimal quantity() {
    return quantity;
  }

  public BigDecimal price() {
    return price;
  }

  public OffsetDateTime executedAt() {
    return executedAt;
  }

  public BigDecimal realizedPnl() {
    return realizedPnl;
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
