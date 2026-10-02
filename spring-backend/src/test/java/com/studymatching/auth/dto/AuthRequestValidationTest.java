package com.studymatching.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestValidationTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void registerRejectsEmailLongerThanDatabaseColumn() {
        RegisterRequest request = new RegisterRequest(
                "testuser",
                "password123",
                "a".repeat(89) + "@example.com"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email");
    }

    @Test
    void registerRejectsMultibytePasswordLongerThan72Bytes() {
        RegisterRequest request = new RegisterRequest(
                "testuser",
                "가".repeat(25),
                "test@example.com"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
    }

    @Test
    void registerAcceptsPasswordExactly72Bytes() {
        RegisterRequest request = new RegisterRequest(
                "testuser",
                "a".repeat(72),
                "test@example.com"
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void loginRejectsPasswordLongerThan72Bytes() {
        LoginRequest request = new LoginRequest("testuser", "a".repeat(73));

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("password");
    }
}
