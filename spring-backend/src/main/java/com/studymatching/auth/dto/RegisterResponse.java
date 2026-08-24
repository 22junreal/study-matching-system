package com.studymatching.auth.dto;

public record RegisterResponse(
        Long id,
        String username,
        String email
) {
}