package com.bloomfield.terminal.user.api.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
