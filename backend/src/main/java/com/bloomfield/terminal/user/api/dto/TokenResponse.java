package com.bloomfield.terminal.user.api.dto;

/**
 * Response body for {@code /auth/login} and {@code /auth/refresh}. The refresh token is not in the
 * body; it is returned as an {@code HttpOnly} cookie so it cannot be read by JavaScript.
 */
public record TokenResponse(String accessToken, long expiresIn) {}
