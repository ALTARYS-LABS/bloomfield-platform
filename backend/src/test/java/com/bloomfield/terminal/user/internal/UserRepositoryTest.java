package com.bloomfield.terminal.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bloomfield.terminal.user.AbstractPostgresIT;
import com.bloomfield.terminal.user.api.Role;
import com.bloomfield.terminal.user.domain.UserAccount;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;

class UserRepositoryTest extends AbstractPostgresIT {

  @Autowired UserRepository userRepository;

  @Test
  void savesAndLoadsUserWithRoles() {
    UserAccount user =
        UserAccount.newUser(
            UUID.randomUUID(),
            "alice-" + UUID.randomUUID() + "@example.com",
            "hash",
            "Alice",
            true,
            OffsetDateTime.now(ZoneOffset.UTC),
            RoleMapper.toRefs(Set.of(Role.ADMIN, Role.VIEWER)));

    UserAccount saved = userRepository.save(user);
    UserAccount loaded = userRepository.findById(saved.id()).orElseThrow();

    assertThat(loaded.email()).isEqualTo(user.email());
    assertThat(RoleMapper.fromRefs(loaded.roles()))
        .containsExactlyInAnyOrder(Role.ADMIN, Role.VIEWER);
  }

  @Test
  void uniqueEmailConstraint() {
    String email = "dup-" + UUID.randomUUID() + "@example.com";
    UserAccount first =
        UserAccount.newUser(
            UUID.randomUUID(),
            email,
            "hash",
            "First",
            true,
            OffsetDateTime.now(ZoneOffset.UTC),
            RoleMapper.toRefs(Set.of(Role.VIEWER)));
    userRepository.save(first);

    UserAccount duplicate =
        UserAccount.newUser(
            UUID.randomUUID(),
            email,
            "hash",
            "Second",
            true,
            OffsetDateTime.now(ZoneOffset.UTC),
            RoleMapper.toRefs(Set.of(Role.VIEWER)));

    assertThatThrownBy(() -> userRepository.save(duplicate))
        .isInstanceOfAny(
            DbActionExecutionException.class,
            org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void findByEmailReturnsUser() {
    String email = "find-" + UUID.randomUUID() + "@example.com";
    UserAccount user =
        UserAccount.newUser(
            UUID.randomUUID(),
            email,
            "hash",
            "Find",
            true,
            OffsetDateTime.now(ZoneOffset.UTC),
            RoleMapper.toRefs(Set.of(Role.ANALYST)));
    userRepository.save(user);

    assertThat(userRepository.findByEmail(email)).isPresent();
  }
}
