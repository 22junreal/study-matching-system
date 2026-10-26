package com.studymatching.common.exception;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import com.studymatching.studyapplication.entity.StudyApplication;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.access.AccessDeniedException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Min;

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

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    void malformedJsonHasConsistentBody() throws Exception {
        mockMvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unexpectedExceptionIsSanitized(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
        assertThat(output.getAll())
                .contains("IllegalStateException: secret SQL details")
                .contains("GlobalExceptionHandlerTest$TestController.unexpected");
    }

    @Test
    void authenticationServiceFailureIsNotBadCredentials() throws Exception {
        mockMvc.perform(get("/test/auth-service-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void optimisticConflictReturns409() throws Exception {
        mockMvc.perform(get("/test/optimistic-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void missingBodyReturnsConsistent400() throws Exception {
        mockMvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void mvcMethodValidationReturnsConsistent400() throws Exception {
        mockMvc.perform(get("/test/page").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unsupportedMethodKeeps405AndAllowHeader() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void mvcAccessDeniedReturns403() throws Exception {
        mockMvc.perform(get("/test/security-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void rawJpaOptimisticConflictReturns409() throws Exception {
        mockMvc.perform(get("/test/jpa-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/page")
        public void page(@RequestParam
                         @Min(0) int page) {
        }

        @GetMapping("/security-denied")
        public void securityDenied() {
            throw new AccessDeniedException("private details");
        }

        @GetMapping("/jpa-conflict")
        public void jpaConflict() {
            throw new OptimisticLockException("stale version");
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new IllegalStateException("secret SQL details");
        }

        @GetMapping("/auth-service-failure")
        public void authServiceFailure() {
            throw new InternalAuthenticationServiceException("DB unavailable");
        }

        @GetMapping("/optimistic-conflict")
        public void optimisticConflict() {
            throw new ObjectOptimisticLockingFailureException(
                    StudyApplication.class, 1L);
        }

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
