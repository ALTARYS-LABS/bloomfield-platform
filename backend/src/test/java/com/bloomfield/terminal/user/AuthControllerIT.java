package com.bloomfield.terminal.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.bloomfield.terminal.user.api.dto.LoginRequest;
import com.bloomfield.terminal.user.api.dto.MeResponse;
import com.bloomfield.terminal.user.api.dto.RegisterRequest;
import com.bloomfield.terminal.user.api.dto.TokenResponse;
import java.util.List;
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
    var email = "flow-" + UUID.randomUUID() + "@example.com";
    var password = "password123";

    ResponseEntity<Void> register =
        rest.postForEntity(
            "/auth/register", new RegisterRequest(email, password, "Flow User"), Void.class);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Login: body carries access token only; refresh token is in a Set-Cookie header.
    ResponseEntity<TokenResponse> login =
        rest.postForEntity("/auth/login", new LoginRequest(email, password), TokenResponse.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    TokenResponse tokens = login.getBody();
    assertThat(tokens).isNotNull();
    assertThat(tokens.accessToken()).isNotBlank();
    assertThat(tokens.expiresIn()).isPositive();

    var firstRefreshCookie = requireRefreshCookie(login.getHeaders());
    assertRefreshCookieAttributes(firstRefreshCookie);

    // /auth/me with bearer returns the user.
    HttpHeaders bearer = new HttpHeaders();
    bearer.setBearerAuth(tokens.accessToken());
    ResponseEntity<MeResponse> me =
        rest.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(bearer), MeResponse.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody()).isNotNull();
    assertThat(me.getBody().email()).isEqualTo(email);

    // No bearer -> 401.
    ResponseEntity<String> unauth = rest.getForEntity("/auth/me", String.class);
    assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // Refresh rotates: send the first cookie, receive a new one.
    HttpHeaders refreshHeaders = new HttpHeaders();
    refreshHeaders.add(HttpHeaders.COOKIE, "refresh_token=" + firstRefreshCookie.value());
    ResponseEntity<TokenResponse> refresh =
        rest.exchange(
            "/auth/refresh",
            HttpMethod.POST,
            new HttpEntity<>(refreshHeaders),
            TokenResponse.class);
    assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
    var rotatedCookie = requireRefreshCookie(refresh.getHeaders());
    assertThat(rotatedCookie.value()).isNotEqualTo(firstRefreshCookie.value());

    // Reusing the old refresh cookie is rejected.
    ResponseEntity<String> reuse =
        rest.exchange(
            "/auth/refresh", HttpMethod.POST, new HttpEntity<>(refreshHeaders), String.class);
    assertThat(reuse.getStatusCode().is4xxClientError()).isTrue();

    // Calling /auth/refresh without any cookie returns 401.
    ResponseEntity<String> noCookie =
        rest.exchange("/auth/refresh", HttpMethod.POST, HttpEntity.EMPTY, String.class);
    assertThat(noCookie.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // Logout clears the cookie.
    HttpHeaders logoutHeaders = new HttpHeaders();
    logoutHeaders.add(HttpHeaders.COOKIE, "refresh_token=" + rotatedCookie.value());
    ResponseEntity<Void> logout =
        rest.exchange("/auth/logout", HttpMethod.POST, new HttpEntity<>(logoutHeaders), Void.class);
    assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    var clearCookie = requireRefreshCookie(logout.getHeaders());
    assertThat(clearCookie.value()).isEmpty();
    assertThat(clearCookie.maxAge()).isEqualTo("0");
  }

  private static ParsedCookie requireRefreshCookie(HttpHeaders headers) {
    List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
    assertThat(setCookies).as("Set-Cookie header present").isNotNull().isNotEmpty();
    return setCookies.stream()
        .filter(c -> c.startsWith("refresh_token="))
        .map(ParsedCookie::of)
        .findFirst()
        .orElseThrow(() -> new AssertionError("refresh_token cookie not found: " + setCookies));
  }

  private static void assertRefreshCookieAttributes(ParsedCookie cookie) {
    assertThat(cookie.raw()).contains("HttpOnly");
    assertThat(cookie.raw()).contains("SameSite=Strict");
    assertThat(cookie.raw()).contains("Path=/auth");
  }

  private record ParsedCookie(String raw, String value, String maxAge) {
    static ParsedCookie of(String raw) {
      String value = "";
      String maxAge = "";
      for (String part : raw.split(";")) {
        String trimmed = part.trim();
        if (trimmed.startsWith("refresh_token=")) {
          value = trimmed.substring("refresh_token=".length());
        } else if (trimmed.startsWith("Max-Age=")) {
          maxAge = trimmed.substring("Max-Age=".length());
        }
      }
      return new ParsedCookie(raw, value, maxAge);
    }
  }
}
