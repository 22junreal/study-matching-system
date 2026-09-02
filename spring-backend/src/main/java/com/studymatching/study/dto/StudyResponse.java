package com.studymatching.study.dto;

import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.entity.StudyStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record StudyResponse(
        Long id,
        Long ownerId,
        String ownerUsername,
        String title,
        String description,
        StudyCategory category,
        StudyLevel level,
        String days,
        LocalTime startTime,
        LocalTime endTime,
        Integer maxMembers,
        StudyStatus status,
        LocalDateTime createdAt
) {
}