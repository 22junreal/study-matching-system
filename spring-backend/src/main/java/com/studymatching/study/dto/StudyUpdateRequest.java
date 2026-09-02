package com.studymatching.study.dto;

import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record StudyUpdateRequest(

        @NotBlank
        @Size(max = 100)
        String title,

        String description,

        @NotNull
        StudyCategory category,

        @NotNull
        StudyLevel level,

        String days,

        LocalTime startTime,

        LocalTime endTime,

        @NotNull
        @Min(1)
        Integer maxMembers
) {
}