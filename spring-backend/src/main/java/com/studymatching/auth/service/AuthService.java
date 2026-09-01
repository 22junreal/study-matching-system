package com.studymatching.auth.service;

import com.studymatching.auth.dto.LoginRequest;
import com.studymatching.auth.dto.LoginResponse;
import com.studymatching.auth.dto.RegisterRequest;
import com.studymatching.auth.dto.RegisterResponse;
import com.studymatching.auth.security.JwtTokenService;
import com.studymatching.common.exception.DuplicateMemberException;
import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        if (memberRepository.existsByUsername(request.username())) {
            throw new DuplicateMemberException("이미 사용 중인 사용자명입니다.");
        }

        if (memberRepository.existsByEmail(request.email())) {
            throw new DuplicateMemberException("이미 사용 중인 이메일입니다.");
        }

        String encodedPassword =
                passwordEncoder.encode(request.password());

        Member member = new Member(
                request.username(),
                encodedPassword,
                request.email()
        );

        Member savedMember = memberRepository.save(member);

        return new RegisterResponse(
                savedMember.getId(),
                savedMember.getUsername(),
                savedMember.getEmail()
        );
    }

    public LoginResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.username(),
                                request.password()
                        )
                );

        String accessToken =
                jwtTokenService.createAccessToken(
                        authentication.getName()
                );

        return new LoginResponse(
                accessToken,
                "Bearer"
        );
    }
}