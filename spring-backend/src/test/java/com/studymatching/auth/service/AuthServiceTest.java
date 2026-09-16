package com.studymatching.auth.service;

import com.studymatching.auth.dto.LoginRequest;
import com.studymatching.auth.dto.LoginResponse;
import com.studymatching.auth.dto.RegisterRequest;
import com.studymatching.auth.dto.RegisterResponse;
import com.studymatching.common.exception.DuplicateMemberException;
import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import com.studymatching.profile.repository.ProfileRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class AuthServiceTest {
    @Autowired
    private StudyApplicationRepository studyApplicationRepository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        studyApplicationRepository.deleteAll();
        studyRepository.deleteAll();
        profileRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void registerSuccess() {

        RegisterRequest request =
                new RegisterRequest(
                        "testuser",
                        "password123",
                        "testuser@test.com"
                );

        RegisterResponse response =
                authService.register(request);

        assertThat(response.id())
                .isNotNull();

        assertThat(response.username())
                .isEqualTo("testuser");

        assertThat(response.email())
                .isEqualTo("testuser@test.com");

        Member savedMember =
                memberRepository.findByUsername("testuser")
                        .orElseThrow();

        assertThat(savedMember.getUsername())
                .isEqualTo("testuser");

        assertThat(savedMember.getEmail())
                .isEqualTo("testuser@test.com");
    }

    @Test
    void passwordIsEncodedWhenRegistering() {

        RegisterRequest request =
                new RegisterRequest(
                        "testuser",
                        "password123",
                        "testuser@test.com"
                );

        authService.register(request);

        Member savedMember =
                memberRepository.findByUsername("testuser")
                        .orElseThrow();

        assertThat(savedMember.getPassword())
                .isNotEqualTo("password123");

        assertThat(
                passwordEncoder.matches(
                        "password123",
                        savedMember.getPassword()
                )
        ).isTrue();
    }

    @Test
    void duplicateUsernameCannotRegister() {

        authService.register(
                new RegisterRequest(
                        "testuser",
                        "password123",
                        "first@test.com"
                )
        );

        RegisterRequest duplicateRequest =
                new RegisterRequest(
                        "testuser",
                        "password456",
                        "second@test.com"
                );

        assertThatThrownBy(() ->
                authService.register(duplicateRequest)
        )
                .isInstanceOf(
                        DuplicateMemberException.class
                )
                .hasMessage(
                        "이미 사용 중인 사용자명입니다."
                );
    }

    @Test
    void duplicateEmailCannotRegister() {

        authService.register(
                new RegisterRequest(
                        "firstuser",
                        "password123",
                        "same@test.com"
                )
        );

        RegisterRequest duplicateRequest =
                new RegisterRequest(
                        "seconduser",
                        "password456",
                        "same@test.com"
                );

        assertThatThrownBy(() ->
                authService.register(duplicateRequest)
        )
                .isInstanceOf(
                        DuplicateMemberException.class
                )
                .hasMessage(
                        "이미 사용 중인 이메일입니다."
                );
    }

    @Test
    void loginSuccess() {

        authService.register(
                new RegisterRequest(
                        "testuser",
                        "password123",
                        "testuser@test.com"
                )
        );

        LoginRequest request =
                new LoginRequest(
                        "testuser",
                        "password123"
                );

        LoginResponse response =
                authService.login(request);

        assertThat(response)
                .isNotNull();

        assertThat(response.accessToken())
                .isNotBlank();

        assertThat(response.tokenType())
                .isEqualTo("Bearer");
    }

    @Test
    void loginFailsWithWrongPassword() {

        authService.register(
                new RegisterRequest(
                        "testuser",
                        "password123",
                        "testuser@test.com"
                )
        );

        LoginRequest request =
                new LoginRequest(
                        "testuser",
                        "wrongpassword"
                );

        assertThatThrownBy(() ->
                authService.login(request)
        )
                .isInstanceOf(
                        AuthenticationException.class
                );
    }

    @Test
    void loginFailsWhenUserDoesNotExist() {

        LoginRequest request =
                new LoginRequest(
                        "unknownuser",
                        "password123"
                );

        assertThatThrownBy(() ->
                authService.login(request)
        )
                .isInstanceOf(
                        AuthenticationException.class
                );
    }
}