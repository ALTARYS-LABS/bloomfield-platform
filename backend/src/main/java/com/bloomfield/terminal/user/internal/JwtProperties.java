package com.bloomfield.terminal.user.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret, String issuer, Duration accessTtl, Duration refreshTtl) {}
