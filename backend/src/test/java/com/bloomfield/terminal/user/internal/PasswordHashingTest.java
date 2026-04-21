package com.bloomfield.terminal.user.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.user.AbstractPostgresIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PasswordHashingTest extends AbstractPostgresIT {

  @Autowired UserService userService;
  @Autowired UserRepository userRepository;

  @Test
  void passwordStoredAsBcryptHash() {
    String email = "bcrypt-" + UUID.randomUUID() + "@example.com";
    String plain = "plaintext-password";
    userService.register(email, plain, "Bcrypt Test");

    var user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.passwordHash()).isNotEqualTo(plain);
    assertThat(user.passwordHash()).matches("^\\$2[aby]\\$.*");
  }
}
