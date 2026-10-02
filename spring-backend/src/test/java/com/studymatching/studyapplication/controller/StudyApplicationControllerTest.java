package com.studymatching.studyapplication.controller;

import com.studymatching.auth.security.CustomUserDetailsService;
import com.studymatching.common.config.SecurityConfig;
import com.studymatching.studyapplication.dto.StudyApplicationResponse;
import com.studymatching.studyapplication.entity.ApplicationStatus;
import com.studymatching.studyapplication.service.StudyApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyApplicationController.class)
@Import(SecurityConfig.class)
class StudyApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyApplicationService applicationService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void applyReturns201AndDelegatesAuthenticatedUsername() throws Exception {
        given(applicationService.apply(1L, "applicant"))
                .willReturn(response(ApplicationStatus.PENDING));

        mockMvc.perform(post("/api/studies/1/applications")
                        .with(jwt().jwt(jwt -> jwt.subject("applicant"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(applicationService).apply(1L, "applicant");
    }

    @Test
    void getApplicationsReturnsApplicationList() throws Exception {
        given(applicationService.getApplications(1L, "owner"))
                .willReturn(List.of(response(ApplicationStatus.PENDING)));

        mockMvc.perform(get("/api/studies/1/applications")
                        .with(jwt().jwt(jwt -> jwt.subject("owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].applicantUsername").value("applicant"));

        verify(applicationService).getApplications(1L, "owner");
    }

    @Test
    void approveDelegatesToService() throws Exception {
        given(applicationService.approve(1L, 10L, "owner"))
                .willReturn(response(ApplicationStatus.APPROVED));

        mockMvc.perform(patch("/api/studies/1/applications/10/approve")
                        .with(jwt().jwt(jwt -> jwt.subject("owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(applicationService).approve(1L, 10L, "owner");
    }

    @Test
    void rejectDelegatesToService() throws Exception {
        given(applicationService.reject(1L, 10L, "owner"))
                .willReturn(response(ApplicationStatus.REJECTED));

        mockMvc.perform(patch("/api/studies/1/applications/10/reject")
                        .with(jwt().jwt(jwt -> jwt.subject("owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(applicationService).reject(1L, 10L, "owner");
    }

    @Test
    void cancelDelegatesToService() throws Exception {
        given(applicationService.cancel(1L, 10L, "applicant"))
                .willReturn(response(ApplicationStatus.CANCELED));

        mockMvc.perform(patch("/api/studies/1/applications/10/cancel")
                        .with(jwt().jwt(jwt -> jwt.subject("applicant"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        verify(applicationService).cancel(1L, 10L, "applicant");
    }

    @Test
    void reapplyDelegatesToService() throws Exception {
        given(applicationService.reapply(1L, 10L, "applicant"))
                .willReturn(response(ApplicationStatus.PENDING));

        mockMvc.perform(patch("/api/studies/1/applications/10/reapply")
                        .with(jwt().jwt(jwt -> jwt.subject("applicant"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(applicationService).reapply(1L, 10L, "applicant");
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(post("/api/studies/1/applications"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(applicationService);
    }

    private StudyApplicationResponse response(ApplicationStatus status) {
        return new StudyApplicationResponse(
                10L,
                1L,
                "Java Study",
                2L,
                "applicant",
                status,
                LocalDateTime.of(2026, 10, 2, 12, 0)
        );
    }
}
