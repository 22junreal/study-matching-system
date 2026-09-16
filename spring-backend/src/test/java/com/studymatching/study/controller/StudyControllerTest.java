package com.studymatching.study.controller;

import com.studymatching.auth.security.CustomUserDetailsService;
import com.studymatching.common.config.SecurityConfig;
import com.studymatching.study.dto.StudyPageResponse;
import com.studymatching.study.dto.StudyResponse;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.entity.StudyStatus;
import com.studymatching.study.service.StudyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudyController.class)
@Import(SecurityConfig.class)
class StudyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyService studyService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @ParameterizedTest(name = "category={0}, level={1}, status={2}")
    @CsvSource({
            ", ,",
            "AI, ,",
            ", BEGINNER,",
            ", , RECRUITING",
            "AI, BEGINNER,",
            "AI, , RECRUITING",
            ", BEGINNER, RECRUITING",
            "AI, BEGINNER, RECRUITING"
    })
    void forwardsEveryFilterCombination(
            StudyCategory category,
            StudyLevel level,
            StudyStatus studyStatus
    ) throws Exception {
        StudyResponse study = new StudyResponse(
                1L, 2L, "owner", "AI study", "Study together",
                StudyCategory.AI, StudyLevel.BEGINNER, "MON",
                LocalTime.of(19, 0), LocalTime.of(21, 0), 5,
                StudyStatus.RECRUITING, LocalDateTime.of(2026, 9, 16, 12, 0)
        );
        given(studyService.searchStudies(category, level, studyStatus, 0, 10))
                .willReturn(new StudyPageResponse(
                        List.of(study), 0, 10, 1, 1, true, true
                ));

        MockHttpServletRequestBuilder request = get("/api/studies").with(jwt());
        if (category != null) {
            request.param("category", category.name());
        }
        if (level != null) {
            request.param("level", level.name());
        }
        if (studyStatus != null) {
            request.param("status", studyStatus.name());
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].category").value("AI"))
                .andExpect(jsonPath("$.content[0].level").value("BEGINNER"))
                .andExpect(jsonPath("$.content[0].status").value("RECRUITING"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        verify(studyService).searchStudies(category, level, studyStatus, 0, 10);
        verifyNoMoreInteractions(studyService);
    }

    @Test
    void forwardsExplicitPaginationWithPartialFilter() throws Exception {
        given(studyService.searchStudies(StudyCategory.AI, null, null, 2, 5))
                .willReturn(new StudyPageResponse(
                        List.of(), 2, 5, 0, 0, false, true
                ));

        mockMvc.perform(get("/api/studies")
                        .with(jwt())
                        .param("category", "AI")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(studyService).searchStudies(StudyCategory.AI, null, null, 2, 5);
        verifyNoMoreInteractions(studyService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"category", "level", "status"})
    void invalidFilterReturns400(String parameter) throws Exception {
        mockMvc.perform(get("/api/studies")
                        .with(jwt())
                        .param(parameter, "INVALID"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(studyService);
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/studies").param("category", "AI"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(studyService);
    }
}
