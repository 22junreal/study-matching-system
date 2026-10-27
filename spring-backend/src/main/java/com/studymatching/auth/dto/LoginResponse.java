package com.studymatching.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn
) {
    @Override public String toString() { return "LoginResponse[REDACTED]"; }
}
