package com.studymatching.profile.dto;
import com.studymatching.study.entity.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.List;
public record ProfileUpdateRequest(
        @NotBlank @Size(max = 100) String department,
        @NotNull StudyCategory category,
        @NotNull StudyLevel level,
        @NotEmpty List<@NotNull StudyDay> days,
        @NotNull @JsonFormat(pattern = "HH:mm")
        @tools.jackson.databind.annotation.JsonDeserialize(using = com.studymatching.common.config.MinuteTimeDeserializer.class) LocalTime startTime,
        @NotNull @JsonFormat(pattern = "HH:mm")
        @tools.jackson.databind.annotation.JsonDeserialize(using = com.studymatching.common.config.MinuteTimeDeserializer.class) LocalTime endTime
) { }
