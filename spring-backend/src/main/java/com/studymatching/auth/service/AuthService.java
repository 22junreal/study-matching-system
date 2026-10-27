package com.studymatching.auth.service;

import com.studymatching.auth.dto.LoginRequest;
import com.studymatching.auth.dto.LoginResponse;
import com.studymatching.auth.dto.RegisterRequest;
import com.studymatching.auth.dto.RegisterResponse;
import com.studymatching.common.exception.DuplicateMemberException;
import com.studymatching.common.exception.ErrorCode;
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
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            RefreshTokenService refreshTokenService
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        if (memberRepository.existsByUsername(request.username())) {
            throw new DuplicateMemberException(ErrorCode.DUPLICATE_USERNAME);
        }

        if (memberRepository.existsByEmail(request.email())) {
            throw new DuplicateMemberException(ErrorCode.DUPLICATE_EMAIL);
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

        Member member = memberRepository.findByUsername(authentication.getName()).orElseThrow(
                () -> new org.springframework.security.authentication.AuthenticationServiceException("Authenticated member missing"));
        return refreshTokenService.issue(member);
    }

    public LoginResponse refresh(String refreshToken) {
        return refreshTokenService.rotate(refreshToken);
    }

    public void logout(String refreshToken) {
        refreshTokenService.logout(refreshToken);
    }
}
