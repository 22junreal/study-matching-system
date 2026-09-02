package com.studymatching.member.service;

import com.studymatching.member.dto.MemberResponse;
import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public MemberResponse getMyInfo(String username) {

        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("사용자를 찾을 수 없습니다.")
                );

        return new MemberResponse(
                member.getId(),
                member.getUsername(),
                member.getEmail()
        );
    }
}