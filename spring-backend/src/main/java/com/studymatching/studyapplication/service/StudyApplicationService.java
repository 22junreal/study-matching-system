package com.studymatching.studyapplication.service;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.entity.Study;
import com.studymatching.study.entity.StudyStatus;
import com.studymatching.study.exception.StudyNotFoundException;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.dto.StudyApplicationResponse;
import com.studymatching.studyapplication.entity.StudyApplication;
import com.studymatching.studyapplication.exception.DuplicateStudyApplicationException;
import com.studymatching.studyapplication.exception.OwnStudyApplicationException;
import com.studymatching.studyapplication.exception.StudyNotRecruitingException;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyApplicationService {

    private final StudyApplicationRepository applicationRepository;
    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;

    public StudyApplicationService(
            StudyApplicationRepository applicationRepository,
            StudyRepository studyRepository,
            MemberRepository memberRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.studyRepository = studyRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public StudyApplicationResponse apply(
            Long studyId,
            String username
    ) {

        Study study = studyRepository.findById(studyId)
                .orElseThrow(StudyNotFoundException::new);

        Member applicant = memberRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("사용자를 찾을 수 없습니다.")
                );

        if (study.getStatus() != StudyStatus.RECRUITING) {
            throw new StudyNotRecruitingException();
        }

        if (study.getOwner().getId().equals(applicant.getId())) {
            throw new OwnStudyApplicationException();
        }

        if (applicationRepository.existsByStudyIdAndApplicantId(
                study.getId(),
                applicant.getId()
        )) {
            throw new DuplicateStudyApplicationException();
        }

        StudyApplication application =
                new StudyApplication(study, applicant);

        StudyApplication savedApplication =
                applicationRepository.save(application);

        return toResponse(savedApplication);
    }

    private StudyApplicationResponse toResponse(
            StudyApplication application
    ) {
        return new StudyApplicationResponse(
                application.getId(),
                application.getStudy().getId(),
                application.getStudy().getTitle(),
                application.getApplicant().getId(),
                application.getApplicant().getUsername(),
                application.getStatus(),
                application.getCreatedAt()
        );
    }
}