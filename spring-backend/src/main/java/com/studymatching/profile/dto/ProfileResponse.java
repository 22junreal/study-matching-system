package com.studymatching.profile.dto;

public record ProfileResponse(
        Long id,
        String username,
        String department,
        String preferredCategory,
        String level,
        String availableDays,
        String availableTime
) {
}