package com.studymatching.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType
) {
}