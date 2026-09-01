package com.studymatching.profile.dto;

public record ProfileUpdateRequest(
        String department,
        String preferredCategory,
        String level,
        String availableDays,
        String availableTime
) {
}