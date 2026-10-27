package com.studymatching.study.dto;

import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.entity.StudyStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import com.studymatching.study.entity.StudyDay;
import com.fasterxml.jackson.annotation.JsonFormat;

public record StudyResponse(
        Long id,
        Long ownerId,
        String ownerUsername,
        String title,
        String description,
        StudyCategory category,
        StudyLevel level,
        List<StudyDay> days,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        Integer maxMembers,
        StudyStatus status,
        LocalDateTime createdAt
) {
}