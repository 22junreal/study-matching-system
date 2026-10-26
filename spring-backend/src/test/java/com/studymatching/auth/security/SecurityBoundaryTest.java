package com.studymatching.auth.security;

import com.studymatching.common.config.SecurityConfig;
import com.studymatching.study.controller.StudyController;
import com.studymatching.study.service.StudyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudyController.class)
@Import({SecurityConfig.class, SecurityBoundaryTest.RestrictedRoute.class})
class SecurityBoundaryTest {
    @Autowired MockMvc mvc;
    @MockitoBean StudyService studies;
    @MockitoBean CustomUserDetailsService users;
    @MockitoBean JwtDecoder decoder;

    @Test
    void missingTokenHasJsonBodyAndBearerChallenge() throws Exception {
        mvc.perform(get("/api/studies"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(studies);
    }

    @Test
    void invalidTokenUsesSameBodyInBearerFilter() throws Exception {
        given(decoder.decode("invalid-token")).willThrow(new BadJwtException("Invalid token"));
        mvc.perform(get("/api/studies").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(result -> assertThat(result.getResolvedException()).isNull());
        verifyNoInteractions(studies);
    }

    @Test
    void filterAuthorizationFailureHasJsonBodyAndBearerChallenge() throws Exception {
        mvc.perform(get("/test/restricted").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(result -> assertThat(result.getResolvedException()).isNull());
        verifyNoInteractions(studies);
    }

    // Production currently requires authentication only. Exercise a filter-level
    // denial with a test-only rule, using the production error handler.
    @TestConfiguration(proxyBeanMethods = false)
    static class RestrictedRoute {
        @Bean
        @Order(0)
        SecurityFilterChain restrictedRoute(HttpSecurity http, SecurityErrorHandler errors) throws Exception {
            return http.securityMatcher("/test/restricted")
                    .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("ADMIN"))
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                    .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())
                            .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                    .build();
        }
    }
}
