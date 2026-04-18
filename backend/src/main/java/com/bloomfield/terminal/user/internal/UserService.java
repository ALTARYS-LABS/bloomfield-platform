package com.bloomfield.terminal.user.internal;

import com.bloomfield.terminal.user.api.Role;
import com.bloomfield.terminal.user.domain.RefreshTokenRecord;
import com.bloomfield.terminal.user.domain.UserAccount;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtIssuer jwtIssuer;

  UserService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtIssuer jwtIssuer) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtIssuer = jwtIssuer;
  }

  public UserAccount register(String email, String password, String fullName) {
    if (userRepository.existsByEmail(email)) {
      throw new EmailAlreadyUsedException("Email already registered");
    }
    UserAccount user =
        UserAccount.newUser(
            UUID.randomUUID(),
            email,
            passwordEncoder.encode(password),
            fullName,
            true,
            OffsetDateTime.now(ZoneOffset.UTC),
            RoleMapper.toRefs(Set.of(Role.VIEWER)));
    return userRepository.save(user);
  }

  public TokenPair login(String email, String password) {
    UserAccount user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
    if (!user.enabled()) {
      throw new AccountDisabledException("Account disabled");
    }
    if (!passwordEncoder.matches(password, user.passwordHash())) {
      throw new InvalidCredentialsException("Invalid credentials");
    }
    return jwtIssuer.issue(user);
  }

  public TokenPair refresh(String rawRefreshToken) {
    String hash = JwtIssuer.sha256(rawRefreshToken);
    RefreshTokenRecord record =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
    if (record.revokedAt() != null) {
      throw new InvalidCredentialsException("Refresh token revoked");
    }
    if (record.expiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
      throw new InvalidCredentialsException("Refresh token expired");
    }
    RefreshTokenRecord revoked =
        new RefreshTokenRecord(
            record.id(),
            record.userId(),
            record.tokenHash(),
            record.expiresAt(),
            OffsetDateTime.now(ZoneOffset.UTC));
    refreshTokenRepository.save(revoked);

    UserAccount user =
        userRepository
            .findById(record.userId())
            .orElseThrow(() -> new UserNotFoundException("User not found"));
    return jwtIssuer.issue(user);
  }

  public void logout(String rawRefreshToken) {
    String hash = JwtIssuer.sha256(rawRefreshToken);
    refreshTokenRepository
        .findByTokenHash(hash)
        .ifPresent(
            record -> {
              if (record.revokedAt() == null) {
                refreshTokenRepository.save(
                    new RefreshTokenRecord(
                        record.id(),
                        record.userId(),
                        record.tokenHash(),
                        record.expiresAt(),
                        OffsetDateTime.now(ZoneOffset.UTC)));
              }
            });
  }

  @Transactional(readOnly = true)
  public UserAccount me(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new UserNotFoundException("User not found"));
  }

  @Transactional(readOnly = true)
  public List<UserAccount> listUsers() {
    return userRepository.findAllByOrderByCreatedAtDesc();
  }

  public UserAccount updateRoles(UUID userId, Set<Role> roles) {
    UserAccount user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));
    UserAccount updated =
        new UserAccount(
            user.id(),
            user.email(),
            user.passwordHash(),
            user.fullName(),
            user.enabled(),
            user.createdAt(),
            RoleMapper.toRefs(roles));
    return userRepository.save(updated);
  }

  public UserAccount setEnabled(UUID userId, boolean enabled) {
    UserAccount user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));
    UserAccount updated =
        new UserAccount(
            user.id(),
            user.email(),
            user.passwordHash(),
            user.fullName(),
            enabled,
            user.createdAt(),
            user.roles());
    return userRepository.save(updated);
  }
}
