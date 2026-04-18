package com.bloomfield.terminal.user.internal;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}
