package com.studymatching.profile.controller;

import tools.jackson.databind.ObjectMapper;
import com.studymatching.common.exception.GlobalExceptionHandler;
import com.studymatching.profile.dto.ProfileResponse;
import com.studymatching.profile.dto.ProfileUpdateRequest;
import com.studymatching.profile.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerTest {

    private MockMvc mockMvc;
    private ProfileService profileService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);

        ProfileController profileController =
                new ProfileController(profileService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(profileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
    }

    @Test
    void updateProfileSuccess() throws Exception {

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "AI",
                        "INTERMEDIATE",
                        "MONDAY,WEDNESDAY",
                        "19:00-21:00"
                );

        ProfileResponse response =
                new ProfileResponse(
                        1L,
                        "testuser",
                        "인공지능학과",
                        "AI",
                        "INTERMEDIATE",
                        "MONDAY,WEDNESDAY",
                        "19:00-21:00"
                );

        when(profileService.updateMyProfile(
                eq("testuser"),
                eq(request)
        )).thenReturn(response);

        Principal principal =
                new UsernamePasswordAuthenticationToken(
                        "testuser",
                        "password"
                );

        mockMvc.perform(
                        put("/api/profiles/me")
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username")
                        .value("testuser"))
                .andExpect(jsonPath("$.department")
                        .value("인공지능학과"))
                .andExpect(jsonPath("$.preferredCategory")
                        .value("AI"))
                .andExpect(jsonPath("$.level")
                        .value("INTERMEDIATE"));

        verify(profileService)
                .updateMyProfile(
                        eq("testuser"),
                        eq(request)
                );
    }

    @Test
    void updateProfileFailsWhenDepartmentIsBlank() throws Exception {

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "",
                        "AI",
                        "BEGINNER",
                        "MONDAY",
                        "19:00-21:00"
                );

        Principal principal =
                new UsernamePasswordAuthenticationToken(
                        "testuser",
                        "password"
                );

        mockMvc.perform(
                        put("/api/profiles/me")
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.errors.department")
                        .value("학과는 필수입니다."));
    }

    @Test
    void updateProfileFailsWhenPreferredCategoryIsInvalid()
            throws Exception {

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "INVALID",
                        "BEGINNER",
                        "MONDAY",
                        "19:00-21:00"
                );

        Principal principal =
                new UsernamePasswordAuthenticationToken(
                        "testuser",
                        "password"
                );

        mockMvc.perform(
                        put("/api/profiles/me")
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.preferredCategory")
                        .value("선호 카테고리 값이 올바르지 않습니다."));
    }

    @Test
    void updateProfileFailsWhenLevelIsInvalid() throws Exception {

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "AI",
                        "MASTER",
                        "MONDAY",
                        "19:00-21:00"
                );

        Principal principal =
                new UsernamePasswordAuthenticationToken(
                        "testuser",
                        "password"
                );

        mockMvc.perform(
                        put("/api/profiles/me")
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.level")
                        .value("레벨 값이 올바르지 않습니다."));
    }

    @Test
    void updateProfileFailsWhenAvailableDaysIsInvalid()
            throws Exception {

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "AI",
                        "BEGINNER",
                        "SOMEDAY",
                        "19:00-21:00"
                );

        Principal principal =
                new UsernamePasswordAuthenticationToken(
                        "testuser",
                        "password"
                );

        mockMvc.perform(
                        put("/api/profiles/me")
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.availableDays")
                        .value("가능 요일 형식이 올바르지 않습니다."));
    }

    @Test
    void updateProfileFailsWhenAvailableTimeIsInvalid()
            throws Exception {

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "AI",
                        "BEGINNER",
                        "MONDAY",
                        "25:99-30:00"
                );

        Principal principal =
                new UsernamePasswordAuthenticationToken(
                        "testuser",
                        "password"
                );

        mockMvc.perform(
                        put("/api/profiles/me")
                                .principal(principal)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.availableTime")
                        .value("가능 시간 형식은 HH:mm-HH:mm 이어야 합니다."));
    }
}