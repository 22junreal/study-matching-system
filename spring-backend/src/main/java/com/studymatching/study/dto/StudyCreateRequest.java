package com.studymatching.study.dto;

import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;
import com.studymatching.study.entity.StudyDay;
import com.fasterxml.jackson.annotation.JsonFormat;

public record StudyCreateRequest(

        @NotBlank
        @Size(max = 100)
        String title,

        String description,

        @NotNull
        StudyCategory category,

        @NotNull
        StudyLevel level,

        List<StudyDay> days,

        @JsonFormat(pattern = "HH:mm")
        @tools.jackson.databind.annotation.JsonDeserialize(using = com.studymatching.common.config.MinuteTimeDeserializer.class) LocalTime startTime,

        @JsonFormat(pattern = "HH:mm")
        @tools.jackson.databind.annotation.JsonDeserialize(using = com.studymatching.common.config.MinuteTimeDeserializer.class) LocalTime endTime,

        @NotNull
        @Min(1)
        Integer maxMembers
) {
}