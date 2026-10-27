package com.studymatching.profile.controller;
import com.studymatching.common.exception.GlobalExceptionHandler;
import com.studymatching.profile.dto.*;
import com.studymatching.profile.service.ProfileService;
import com.studymatching.study.entity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfileControllerTest {
    MockMvc mvc;
    ProfileService service;
    ObjectMapper json = new ObjectMapper();
    @BeforeEach void setUp() {
        service = mock(ProfileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProfileController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private Map<String,Object> valid() {
        return new HashMap<>(Map.of("department", "AI", "category", "AI", "level", "BEGINNER",
                "days", List.of("MONDAY"), "startTime", "19:00", "endTime", "21:00"));
    }
    @Test void updateUsesTypedInputAndCanonicalResponse() throws Exception {
        var request = new ProfileUpdateRequest("AI", StudyCategory.AI, StudyLevel.BEGINNER,
                List.of(StudyDay.MONDAY), LocalTime.of(19,0), LocalTime.of(21,0));
        when(service.updateMyProfile("user", request)).thenReturn(new ProfileResponse(1L, "user", "AI",
                StudyCategory.AI, StudyLevel.BEGINNER, List.of(StudyDay.MONDAY), LocalTime.of(19,0), LocalTime.of(21,0)));
        mvc.perform(put("/api/profiles/me").principal(new UsernamePasswordAuthenticationToken("user", "unused"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(valid())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("AI"))
                .andExpect(jsonPath("$.days[0]").value("MONDAY"))
                .andExpect(jsonPath("$.startTime").value("19:00"));
        verify(service).updateMyProfile("user", request);
    }
    @ParameterizedTest
    @ValueSource(strings={"department", "category", "level", "days", "startTime", "endTime"})
    void missingRequiredFieldsAre400(String field) throws Exception {
        var body = valid(); body.remove(field);
        mvc.perform(put("/api/profiles/me").principal(new UsernamePasswordAuthenticationToken("user", "unused"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @ParameterizedTest
    @ValueSource(strings={"category", "level", "days", "startTime"})
    void invalidTypedInputsAre400(String field) throws Exception {
        var body = valid(); body.put(field, field.equals("days") ? List.of("SOMEDAY") : "INVALID");
        mvc.perform(put("/api/profiles/me").principal(new UsernamePasswordAuthenticationToken("user", "unused"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
