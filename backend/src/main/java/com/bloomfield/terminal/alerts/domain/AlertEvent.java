package com.bloomfield.terminal.alerts.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Événement d'alerte déclenché quand une règle se réalise. Peut rester avec delivered_at = null
 * jusqu'à ce que l'utilisateur se reconnecte.
 *
 * <p>UUID pré-généré côté application : voir {@link AlertRule} pour la raison du pattern {@link
 * Persistable}.
 */
@Table("alert_events")
public final class AlertEvent implements Persistable<UUID> {

  @Id private final UUID id;

  @Column("rule_id")
  private final UUID ruleId;

  @Column("user_id")
  private final UUID userId;

  private final String ticker;

  @Column("triggered_at")
  private final Instant triggeredAt;

  private final BigDecimal price;

  @Column("delivered_at")
  private final Instant deliveredAt;

  @Column("read_at")
  private final Instant readAt;

  @Transient private final boolean isNew;

  @PersistenceCreator
  public AlertEvent(
      UUID id,
      UUID ruleId,
      UUID userId,
      String ticker,
      Instant triggeredAt,
      BigDecimal price,
      Instant deliveredAt,
      Instant readAt) {
    this(id, ruleId, userId, ticker, triggeredAt, price, deliveredAt, readAt, false);
  }

  private AlertEvent(
      UUID id,
      UUID ruleId,
      UUID userId,
      String ticker,
      Instant triggeredAt,
      BigDecimal price,
      Instant deliveredAt,
      Instant readAt,
      boolean isNew) {
    this.id = id;
    this.ruleId = ruleId;
    this.userId = userId;
    this.ticker = ticker;
    this.triggeredAt = triggeredAt;
    this.price = price;
    this.deliveredAt = deliveredAt;
    this.readAt = readAt;
    this.isNew = isNew;
  }

  /** Crée un nouvel événement (INSERT). */
  public static AlertEvent newEvent(
      UUID id, UUID ruleId, UUID userId, String ticker, Instant triggeredAt, BigDecimal price) {
    return new AlertEvent(id, ruleId, userId, ticker, triggeredAt, price, null, null, true);
  }

  public UUID id() {
    return id;
  }

  public UUID ruleId() {
    return ruleId;
  }

  public UUID userId() {
    return userId;
  }

  public String ticker() {
    return ticker;
  }

  public Instant triggeredAt() {
    return triggeredAt;
  }

  public BigDecimal price() {
    return price;
  }

  public Instant deliveredAt() {
    return deliveredAt;
  }

  public Instant readAt() {
    return readAt;
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
