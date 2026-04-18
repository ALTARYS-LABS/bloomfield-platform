package com.bloomfield.terminal.user.web.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
