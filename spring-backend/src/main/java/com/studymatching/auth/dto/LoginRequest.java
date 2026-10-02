package com.studymatching.auth.dto;

import com.studymatching.auth.validation.ValidBcryptPassword;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank
        String username,

        @NotBlank
        @ValidBcryptPassword
        String password
) {
}
