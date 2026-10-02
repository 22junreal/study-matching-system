package com.studymatching.common.exception;

import com.studymatching.study.exception.StudyAccessDeniedException;
import com.studymatching.study.exception.StudyNotFoundException;
import com.studymatching.study.exception.StudyInvalidTimeRangeException;
import com.studymatching.studyapplication.exception.DuplicateStudyApplicationException;
import com.studymatching.studyapplication.exception.OwnStudyApplicationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();

        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void studyNotFoundReturns404() throws Exception {

        mockMvc.perform(
                        get("/test/study-not-found")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void studyAccessDeniedReturns403() throws Exception {

        mockMvc.perform(
                        get("/test/access-denied")
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("해당 스터디에 대한 권한이 없습니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void duplicateStudyApplicationReturns409() throws Exception {

        mockMvc.perform(
                        post("/test/duplicate-application")
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void ownStudyApplicationReturns400() throws Exception {

        mockMvc.perform(
                        post("/test/own-study-application")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void dataIntegrityViolationReturns409() throws Exception {
        mockMvc.perform(post("/test/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("이미 존재하거나 처리할 수 없는 데이터입니다."));
    }

    @Test
    void constraintViolationReturns400() throws Exception {
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void invalidStudyTimeRangeReturns400() throws Exception {
        mockMvc.perform(post("/test/invalid-study-time"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("스터디 시작 시간은 종료 시간보다 빨라야 합니다."));
    }

    @Test
    void validationFailureReturns400AndFieldErrors() throws Exception {

        String requestBody = """
                {
                  "title": ""
                }
                """;

        mockMvc.perform(
                        post("/test/validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors.title")
                        .value("제목은 필수입니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/study-not-found")
        public void studyNotFound() {
            throw new StudyNotFoundException();
        }

        @GetMapping("/access-denied")
        public void accessDenied() {
            throw new StudyAccessDeniedException();
        }

        @PostMapping("/duplicate-application")
        public void duplicateApplication() {
            throw new DuplicateStudyApplicationException();
        }

        @PostMapping("/own-study-application")
        public void ownStudyApplication() {
            throw new OwnStudyApplicationException();
        }

        @PostMapping("/data-integrity")
        public void dataIntegrityViolation() {
            throw new DataIntegrityViolationException("unique constraint");
        }

        @GetMapping("/constraint-violation")
        public void constraintViolation() {
            throw new ConstraintViolationException(java.util.Set.of());
        }

        @PostMapping("/invalid-study-time")
        public void invalidStudyTimeRange() {
            throw new StudyInvalidTimeRangeException();
        }

        @PostMapping("/validation")
        public void validation(
                @Valid @RequestBody ValidationRequest request
        ) {
        }
    }

    record ValidationRequest(

            @NotBlank(message = "제목은 필수입니다.")
            String title

    ) {
    }
}
