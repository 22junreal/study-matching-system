package com.studymatching.auth.dto;

import com.studymatching.auth.validation.ValidBcryptPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(min = 2, max = 50)
        String username,

        @NotBlank
        @Size(min = 8)
        @ValidBcryptPassword
        String password,

        @NotBlank
        @Email
        @Size(max = 100)
        String email
) {
}
