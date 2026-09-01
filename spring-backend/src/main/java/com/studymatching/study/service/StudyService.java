package com.studymatching.study.service;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.dto.StudyCreateRequest;
import com.studymatching.study.dto.StudyResponse;
import com.studymatching.study.entity.Study;
import com.studymatching.study.repository.StudyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyService {

    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;

    public StudyService(
            StudyRepository studyRepository,
            MemberRepository memberRepository
    ) {
        this.studyRepository = studyRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public StudyResponse createStudy(
            String username,
            StudyCreateRequest request
    ) {

        Member owner = memberRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("사용자를 찾을 수 없습니다.")
                );

        Study study = new Study(
                owner,
                request.title(),
                request.description(),
                request.category(),
                request.level(),
                request.days(),
                request.startTime(),
                request.endTime(),
                request.maxMembers()
        );

        Study savedStudy = studyRepository.save(study);

        return toResponse(savedStudy);
    }

    private StudyResponse toResponse(Study study) {

        return new StudyResponse(
                study.getId(),
                study.getOwner().getId(),
                study.getOwner().getUsername(),
                study.getTitle(),
                study.getDescription(),
                study.getCategory(),
                study.getLevel(),
                study.getDays(),
                study.getStartTime(),
                study.getEndTime(),
                study.getMaxMembers(),
                study.getStatus(),
                study.getCreatedAt()
        );
    }
}