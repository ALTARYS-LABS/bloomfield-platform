package com.bloomfield.terminal.portfolio.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

// On pré-génère les UUID côté application, d'où le pattern Persistable pour que Spring Data JDBC
// sache distinguer un insert d'un update sans colonne "version". Même convention que UserAccount.
@Table("portfolios")
public final class Portfolio implements Persistable<UUID> {

  @Id private final UUID id;

  @Column("user_id")
  private final UUID userId;

  private final String name;

  @Column("created_at")
  private final OffsetDateTime createdAt;

  @Transient private final boolean isNew;

  @PersistenceCreator
  public Portfolio(UUID id, UUID userId, String name, OffsetDateTime createdAt) {
    this(id, userId, name, createdAt, false);
  }

  private Portfolio(UUID id, UUID userId, String name, OffsetDateTime createdAt, boolean isNew) {
    this.id = id;
    this.userId = userId;
    this.name = name;
    this.createdAt = createdAt;
    this.isNew = isNew;
  }

  public static Portfolio newPortfolio(
      UUID id, UUID userId, String name, OffsetDateTime createdAt) {
    return new Portfolio(id, userId, name, createdAt, true);
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public String name() {
    return name;
  }

  public OffsetDateTime createdAt() {
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
