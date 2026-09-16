package com.studymatching.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(

        @NotBlank(message = "학과는 필수입니다.")
        @Size(max = 100, message = "학과는 100자 이하여야 합니다.")
        String department,

        @NotBlank(message = "선호 카테고리는 필수입니다.")
        @Pattern(
                regexp = "PROGRAMMING|AI|DATA|LANGUAGE|CERTIFICATE|ETC",
                message = "선호 카테고리 값이 올바르지 않습니다."
        )
        String preferredCategory,

        @NotBlank(message = "레벨은 필수입니다.")
        @Pattern(
                regexp = "BEGINNER|INTERMEDIATE|ADVANCED",
                message = "레벨 값이 올바르지 않습니다."
        )
        String level,

        @NotBlank(message = "가능 요일은 필수입니다.")
        @Pattern(
                regexp = "(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)(,(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY))*",
                message = "가능 요일 형식이 올바르지 않습니다."
        )
        String availableDays,

        @NotBlank(message = "가능 시간은 필수입니다.")
        @Pattern(
                regexp = "([01]\\d|2[0-3]):[0-5]\\d-([01]\\d|2[0-3]):[0-5]\\d",
                message = "가능 시간 형식은 HH:mm-HH:mm 이어야 합니다."
        )
        String availableTime
) {
}