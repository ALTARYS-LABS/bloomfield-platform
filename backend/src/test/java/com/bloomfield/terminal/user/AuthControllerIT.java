package com.bloomfield.terminal.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.user.web.dto.LoginRequest;
import com.bloomfield.terminal.user.web.dto.MeResponse;
import com.bloomfield.terminal.user.web.dto.RefreshRequest;
import com.bloomfield.terminal.user.web.dto.RegisterRequest;
import com.bloomfield.terminal.user.web.dto.TokenResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthControllerIT extends AbstractPostgresIT {

  @Autowired TestRestTemplate rest;

  @Test
  void registerLoginMeRefreshFlow() {
    String email = "flow-" + UUID.randomUUID() + "@example.com";
    String password = "password123";

    ResponseEntity<Void> register =
        rest.postForEntity(
            "/auth/register", new RegisterRequest(email, password, "Flow User"), Void.class);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<TokenResponse> login =
        rest.postForEntity("/auth/login", new LoginRequest(email, password), TokenResponse.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    TokenResponse tokens = login.getBody();
    assertThat(tokens).isNotNull();
    assertThat(tokens.accessToken()).isNotBlank();
    assertThat(tokens.refreshToken()).isNotBlank();
    assertThat(tokens.expiresIn()).isPositive();

    HttpHeaders auth = new HttpHeaders();
    auth.setBearerAuth(tokens.accessToken());
    ResponseEntity<MeResponse> me =
        rest.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(auth), MeResponse.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody()).isNotNull();
    assertThat(me.getBody().email()).isEqualTo(email);

    // unauth on protected endpoint
    ResponseEntity<String> unauth = rest.getForEntity("/auth/me", String.class);
    assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // refresh rotates
    ResponseEntity<TokenResponse> refresh =
        rest.postForEntity(
            "/auth/refresh", new RefreshRequest(tokens.refreshToken()), TokenResponse.class);
    assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
    TokenResponse rotated = refresh.getBody();
    assertThat(rotated).isNotNull();
    assertThat(rotated.refreshToken()).isNotEqualTo(tokens.refreshToken());

    // old refresh is rejected
    ResponseEntity<String> reuse =
        rest.postForEntity(
            "/auth/refresh", new RefreshRequest(tokens.refreshToken()), String.class);
    assertThat(reuse.getStatusCode().is4xxClientError()).isTrue();
  }
}
