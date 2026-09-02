package com.studymatching.studyapplication.dto;

import com.studymatching.studyapplication.entity.ApplicationStatus;

import java.time.LocalDateTime;

public record StudyApplicationResponse(
        Long id,
        Long studyId,
        String studyTitle,
        Long applicantId,
        String applicantUsername,
        ApplicationStatus status,
        LocalDateTime createdAt
) {
}