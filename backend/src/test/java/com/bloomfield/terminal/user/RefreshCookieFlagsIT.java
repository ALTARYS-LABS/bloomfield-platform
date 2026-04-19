package com.bloomfield.terminal.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.user.api.dto.LoginRequest;
import com.bloomfield.terminal.user.api.dto.RegisterRequest;
import com.bloomfield.terminal.user.api.dto.TokenResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vérifie qu'en profil prod le cookie de refresh token porte bien les attributs requis pour une
 * utilisation sur HTTPS : {@code HttpOnly}, {@code Secure}, {@code SameSite=Strict}, {@code
 * Path=/auth}. Le flag {@code Secure} n'est positionné qu'en prod ({@code application-prod.yml}),
 * d'où le profil prod actif dans ce test.
 */
@ActiveProfiles("prod")
class RefreshCookieFlagsIT extends AbstractPostgresIT {

  @Autowired TestRestTemplate rest;

  @Test
  void loginSetCookieCarriesAllHardenedFlags() {
    var email = "flags-" + UUID.randomUUID() + "@example.com";
    var password = "password123";

    rest.postForEntity(
        "/auth/register", new RegisterRequest(email, password, "Flags User"), Void.class);

    ResponseEntity<TokenResponse> login =
        rest.postForEntity("/auth/login", new LoginRequest(email, password), TokenResponse.class);

    List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(setCookies).as("Set-Cookie header present").isNotNull().isNotEmpty();
    var refresh =
        setCookies.stream()
            .filter(c -> c.startsWith("refresh_token="))
            .findFirst()
            .orElseThrow(() -> new AssertionError("refresh_token cookie absent: " + setCookies));

    assertThat(refresh).contains("HttpOnly");
    assertThat(refresh).contains("Secure");
    assertThat(refresh).contains("SameSite=Strict");
    assertThat(refresh).contains("Path=/auth");
  }
}
