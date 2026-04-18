package com.bloomfield.terminal.user.internal;

import com.bloomfield.terminal.user.api.Role;
import com.bloomfield.terminal.user.domain.RefreshTokenRecord;
import com.bloomfield.terminal.user.domain.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class JwtIssuer {

  private final JwtEncoder jwtEncoder;
  private final JwtProperties jwtProperties;
  private final RefreshTokenRepository refreshTokenRepository;
  private final SecureRandom random = new SecureRandom();

  JwtIssuer(
      JwtEncoder jwtEncoder,
      JwtProperties jwtProperties,
      RefreshTokenRepository refreshTokenRepository) {
    this.jwtEncoder = jwtEncoder;
    this.jwtProperties = jwtProperties;
    this.refreshTokenRepository = refreshTokenRepository;
  }

  TokenPair issue(UserAccount user) {
    Instant now = Instant.now();
    Instant accessExp = now.plus(jwtProperties.accessTtl());
    Set<Role> roles = RoleMapper.fromRefs(user.roles());
    List<String> roleNames = roles.stream().map(Enum::name).toList();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .issuedAt(now)
            .expiresAt(accessExp)
            .subject(user.id().toString())
            .claim("email", user.email())
            .claim("roles", roleNames)
            .build();

    String accessToken =
        jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build(),
                    claims))
            .getTokenValue();

    byte[] raw = new byte[32];
    random.nextBytes(raw);
    String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    String hash = sha256(rawRefresh);

    OffsetDateTime refreshExp =
        OffsetDateTime.ofInstant(now.plus(jwtProperties.refreshTtl()), ZoneOffset.UTC);
    RefreshTokenRecord record =
        RefreshTokenRecord.newRecord(UUID.randomUUID(), user.id(), hash, refreshExp, null);
    refreshTokenRepository.save(record);

    long expiresIn = jwtProperties.accessTtl().toSeconds();
    return new TokenPair(accessToken, rawRefresh, expiresIn);
  }

  static String sha256(String value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
