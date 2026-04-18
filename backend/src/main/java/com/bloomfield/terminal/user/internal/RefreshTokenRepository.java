package com.bloomfield.terminal.user.internal;

import com.bloomfield.terminal.user.domain.RefreshTokenRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

interface RefreshTokenRepository extends CrudRepository<RefreshTokenRecord, UUID> {
  Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);
}
