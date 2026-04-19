package com.bloomfield.terminal.user.internal;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds refresh-token cookies.
 *
 * <p>Why HttpOnly + Secure + SameSite=Strict: the refresh token must survive reloads and authorise
 * silent re-issuance of access tokens, but must never be readable from JavaScript. Cookie with
 * HttpOnly blocks XSS exfiltration; SameSite=Strict blocks CSRF on cross-site POSTs; Secure ensures
 * the value never leaves the browser over plain HTTP. See {@code
 * _kb_/web-auth-security-tutorial.md}.
 */
@Component
public class AuthCookieFactory {

  private final CookieProperties properties;

  AuthCookieFactory(CookieProperties properties) {
    this.properties = properties;
  }

  public ResponseCookie issue(String refreshToken) {
    return base(refreshToken).maxAge(properties.maxAge()).build();
  }

  public ResponseCookie clear() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(properties.name(), value)
        .httpOnly(true)
        .secure(properties.secure())
        .sameSite(properties.sameSite())
        .path(properties.path());
  }
}
