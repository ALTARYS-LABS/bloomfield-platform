package com.bloomfield.terminal.user.internal;

import com.bloomfield.terminal.user.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

interface UserRepository extends CrudRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmail(String email);

  List<UserAccount> findAllByOrderByCreatedAtDesc();

  boolean existsByEmail(String email);
}
