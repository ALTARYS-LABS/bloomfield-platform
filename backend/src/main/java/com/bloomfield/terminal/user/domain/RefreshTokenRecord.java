package com.bloomfield.terminal.user.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("refresh_tokens")
public final class RefreshTokenRecord implements Persistable<UUID> {

  @Id private final UUID id;

  @Column("user_id")
  private final UUID userId;

  @Column("token_hash")
  private final String tokenHash;

  @Column("expires_at")
  private final OffsetDateTime expiresAt;

  @Column("revoked_at")
  private final OffsetDateTime revokedAt;

  @Transient private final boolean isNew;

  @PersistenceCreator
  public RefreshTokenRecord(
      UUID id, UUID userId, String tokenHash, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    this(id, userId, tokenHash, expiresAt, revokedAt, false);
  }

  private RefreshTokenRecord(
      UUID id,
      UUID userId,
      String tokenHash,
      OffsetDateTime expiresAt,
      OffsetDateTime revokedAt,
      boolean isNew) {
    this.id = id;
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
    this.isNew = isNew;
  }

  public static RefreshTokenRecord newRecord(
      UUID id, UUID userId, String tokenHash, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    return new RefreshTokenRecord(id, userId, tokenHash, expiresAt, revokedAt, true);
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public String tokenHash() {
    return tokenHash;
  }

  public OffsetDateTime expiresAt() {
    return expiresAt;
  }

  public OffsetDateTime revokedAt() {
    return revokedAt;
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
