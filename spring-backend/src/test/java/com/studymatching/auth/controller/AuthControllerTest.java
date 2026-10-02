package com.studymatching.auth.controller;

import com.studymatching.auth.service.AuthService;
import com.studymatching.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void registerRejectsEmailLongerThan100Characters() throws Exception {
        String body = """
                {
                  "username": "testuser",
                  "password": "password123",
                  "email": "%s"
                }
                """.formatted("a".repeat(89) + "@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void registerRejectsPasswordLongerThan72Utf8Bytes() throws Exception {
        String body = """
                {
                  "username": "testuser",
                  "password": "%s",
                  "email": "test@example.com"
                }
                """.formatted("가".repeat(25));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password")
                        .value("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."));

        verifyNoInteractions(authService);
    }

    @Test
    void loginRejectsPasswordLongerThan72Utf8Bytes() throws Exception {
        String body = """
                {
                  "username": "testuser",
                  "password": "%s"
                }
                """.formatted("a".repeat(73));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(authService);
    }
}
