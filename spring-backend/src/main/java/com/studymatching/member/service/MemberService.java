package com.studymatching.member.service;

import com.studymatching.member.dto.MemberResponse;
import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.studymatching.member.exception.MemberNotFoundException;
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public MemberResponse getMyInfo(String username) {

        Member member = memberRepository.findByUsername(username)
                .orElseThrow(MemberNotFoundException::new);


        return new MemberResponse(
                member.getId(),
                member.getUsername(),
                member.getEmail()
        );
    }
}