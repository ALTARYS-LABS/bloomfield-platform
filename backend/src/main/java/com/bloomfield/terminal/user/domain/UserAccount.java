package com.bloomfield.terminal.user.domain;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public final class UserAccount implements Persistable<UUID> {

  @Id private final UUID id;
  private final String email;

  @Column("password_hash")
  private final String passwordHash;

  @Column("full_name")
  private final String fullName;

  private final boolean enabled;

  @Column("created_at")
  private final OffsetDateTime createdAt;

  @MappedCollection(idColumn = "user_id")
  private final Set<UserRoleRef> roles;

  @Transient private final boolean isNew;

  @PersistenceCreator
  public UserAccount(
      UUID id,
      String email,
      String passwordHash,
      String fullName,
      boolean enabled,
      OffsetDateTime createdAt,
      Set<UserRoleRef> roles) {
    this(id, email, passwordHash, fullName, enabled, createdAt, roles, false);
  }

  private UserAccount(
      UUID id,
      String email,
      String passwordHash,
      String fullName,
      boolean enabled,
      OffsetDateTime createdAt,
      Set<UserRoleRef> roles,
      boolean isNew) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.fullName = fullName;
    this.enabled = enabled;
    this.createdAt = createdAt;
    this.roles = roles;
    this.isNew = isNew;
  }

  public static UserAccount newUser(
      UUID id,
      String email,
      String passwordHash,
      String fullName,
      boolean enabled,
      OffsetDateTime createdAt,
      Set<UserRoleRef> roles) {
    return new UserAccount(id, email, passwordHash, fullName, enabled, createdAt, roles, true);
  }

  public UUID id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public String fullName() {
    return fullName;
  }

  public boolean enabled() {
    return enabled;
  }

  public OffsetDateTime createdAt() {
    return createdAt;
  }

  public Set<UserRoleRef> roles() {
    return roles;
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
