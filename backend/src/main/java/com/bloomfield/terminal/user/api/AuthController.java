package com.bloomfield.terminal.user.api;

import com.bloomfield.terminal.user.api.dto.LoginRequest;
import com.bloomfield.terminal.user.api.dto.MeResponse;
import com.bloomfield.terminal.user.api.dto.RegisterRequest;
import com.bloomfield.terminal.user.api.dto.TokenResponse;
import com.bloomfield.terminal.user.internal.AuthCookieFactory;
import com.bloomfield.terminal.user.internal.RoleMapper;
import com.bloomfield.terminal.user.internal.TokenPair;
import com.bloomfield.terminal.user.internal.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints. The refresh token is carried in an {@code HttpOnly; Secure; SameSite=Strict}
 * cookie so it is never reachable from JavaScript (XSS exfiltration defence). {@code /auth/refresh}
 * and {@code /auth/logout} read the token from the cookie; {@code /auth/login} and {@code
 * /auth/refresh} set a rotating cookie on the response. See {@code
 * _kb_/web-auth-security-tutorial.md} for the full rationale.
 */
@RestController
@RequestMapping("/auth")
record AuthController(UserService userService, AuthCookieFactory cookieFactory) {

  @PostMapping("/register")
  ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
    userService.register(request.email(), request.password(), request.fullName());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping("/login")
  ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    var pair = userService.login(request.email(), request.password());
    return withRefreshCookie(pair);
  }

  @PostMapping("/refresh")
  ResponseEntity<TokenResponse> refresh(
      @CookieValue(value = "${app.auth.refresh-cookie.name}", required = false)
          String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    var pair = userService.refresh(refreshToken);
    return withRefreshCookie(pair);
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @CookieValue(value = "${app.auth.refresh-cookie.name}", required = false)
          String refreshToken) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      userService.logout(refreshToken);
    }
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
        .build();
  }

  @GetMapping("/me")
  MeResponse me(@AuthenticationPrincipal Jwt principal) {
    var userId = UUID.fromString(principal.getSubject());
    var user = userService.me(userId);
    return new MeResponse(
        user.id(), user.email(), user.fullName(), RoleMapper.fromRefs(user.roles()));
  }

  private ResponseEntity<TokenResponse> withRefreshCookie(TokenPair pair) {
    ResponseCookie cookie = cookieFactory.issue(pair.refreshToken());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(new TokenResponse(pair.accessToken(), pair.expiresInSeconds()));
  }
}
