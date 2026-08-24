package com.studymatching.auth.service;

import com.studymatching.auth.dto.RegisterRequest;
import com.studymatching.auth.dto.RegisterResponse;
import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.common.exception.DuplicateMemberException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
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
}