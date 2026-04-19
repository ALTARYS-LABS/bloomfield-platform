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
 * Règle d'alerte définie par un utilisateur. Une fois déclenchée, la règle est désactivée
 * (comportement "one-shot" pour la démo v2).
 *
 * <p>UUID pré-généré côté application : on implémente {@link Persistable} pour que Spring Data JDBC
 * distingue un INSERT d'un UPDATE sans colonne "version", à l'identique du module portfolio.
 */
@Table("alert_rules")
public final class AlertRule implements Persistable<UUID> {

  @Id private final UUID id;

  @Column("user_id")
  private final UUID userId;

  private final String ticker;
  private final ThresholdOperator operator;
  private final BigDecimal threshold;
  private final boolean enabled;

  @Column("created_at")
  private final Instant createdAt;

  @Transient private final boolean isNew;

  @PersistenceCreator
  public AlertRule(
      UUID id,
      UUID userId,
      String ticker,
      ThresholdOperator operator,
      BigDecimal threshold,
      boolean enabled,
      Instant createdAt) {
    this(id, userId, ticker, operator, threshold, enabled, createdAt, false);
  }

  private AlertRule(
      UUID id,
      UUID userId,
      String ticker,
      ThresholdOperator operator,
      BigDecimal threshold,
      boolean enabled,
      Instant createdAt,
      boolean isNew) {
    this.id = id;
    this.userId = userId;
    this.ticker = ticker;
    this.operator = operator;
    this.threshold = threshold;
    this.enabled = enabled;
    this.createdAt = createdAt;
    this.isNew = isNew;
  }

  /** Crée une nouvelle règle (INSERT). */
  public static AlertRule newRule(
      UUID id,
      UUID userId,
      String ticker,
      ThresholdOperator operator,
      BigDecimal threshold,
      boolean enabled,
      Instant createdAt) {
    return new AlertRule(id, userId, ticker, operator, threshold, enabled, createdAt, true);
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public String ticker() {
    return ticker;
  }

  public ThresholdOperator operator() {
    return operator;
  }

  public BigDecimal threshold() {
    return threshold;
  }

  public boolean enabled() {
    return enabled;
  }

  public Instant createdAt() {
    return createdAt;
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
