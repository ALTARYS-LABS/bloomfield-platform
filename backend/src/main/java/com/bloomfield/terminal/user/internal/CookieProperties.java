package com.bloomfield.terminal.user.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Attributes for the refresh-token cookie. Externalised so dev (localhost, non-https) and prod
 * (https, SameSite=Strict) can diverge without code changes.
 */
@ConfigurationProperties(prefix = "app.auth.refresh-cookie")
public record CookieProperties(
    String name, String path, String sameSite, boolean secure, Duration maxAge) {}
