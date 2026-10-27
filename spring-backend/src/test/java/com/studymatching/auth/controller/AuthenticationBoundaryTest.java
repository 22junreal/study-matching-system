package com.studymatching.auth.controller;

import com.studymatching.auth.security.CustomUserDetailsService;
import com.studymatching.auth.security.JwtTokenService;
import com.studymatching.auth.service.AuthService;
import com.studymatching.common.config.SecurityConfig;
import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthService.class, CustomUserDetailsService.class})
class AuthenticationBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean MemberRepository members;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean com.studymatching.auth.service.RefreshTokenService refreshTokens;
    @MockitoBean JwtDecoder decoder;

    @Test
    void databaseLookupFailureReturns500ThroughRealAuthenticationProvider() throws Exception {
        given(members.findByUsername("alice"))
                .willThrow(new DataAccessResourceFailureException("private connection details"));
        login().andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
        verifyNoInteractions(tokens);
    }

    @Test
    void absentUserStillReturns401() throws Exception {
        given(members.findByUsername("alice")).willReturn(Optional.empty());
        login().andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(tokens);
    }

    @Test
    void incorrectPasswordStillReturns401() throws Exception {
        given(members.findByUsername("alice")).willReturn(Optional.of(
                new Member("alice", passwordEncoder.encode("correct-password"), "alice@test.com")));
        login().andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(tokens);
    }

    private org.springframework.test.web.servlet.ResultActions login() throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"alice","password":"wrong-password"}
                        """));
    }
}
