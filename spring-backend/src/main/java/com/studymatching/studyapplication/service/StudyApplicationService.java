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
import com.studymatching.study.exception.StudyAccessDeniedException;
import com.studymatching.studyapplication.exception.StudyApplicationNotFoundException;
import com.studymatching.member.exception.MemberNotFoundException;
import com.studymatching.studyapplication.exception.StudyCapacityExceededException;


import java.util.List;
import com.studymatching.common.exception.ErrorCode;
import com.studymatching.studymember.entity.StudyMember;
import com.studymatching.studymember.entity.StudyMemberStatus;
import com.studymatching.studymember.repository.StudyMemberRepository;
import com.studymatching.studymember.exception.StudyMemberException;
import com.studymatching.studyapplication.entity.ApplicationStatus;
@Service
public class StudyApplicationService {

    private final StudyApplicationRepository applicationRepository;
    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;
    private final StudyMemberRepository studyMemberRepository;

    public StudyApplicationService(
            StudyApplicationRepository applicationRepository,
            StudyRepository studyRepository,
            MemberRepository memberRepository,
            StudyMemberRepository studyMemberRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.studyRepository = studyRepository;
        this.memberRepository = memberRepository;
        this.studyMemberRepository = studyMemberRepository;
    }

    @Transactional
    public StudyApplicationResponse apply(
            Long studyId,
            String username
    ) {

        Study study = studyRepository.findByIdForUpdate(studyId)
                .orElseThrow(StudyNotFoundException::new);

        Member applicant = memberRepository.findByUsername(username)
                .orElseThrow(MemberNotFoundException::new);

        if (study.getStatus() != StudyStatus.RECRUITING) {
            throw new StudyNotRecruitingException();
        }

        if (study.getOwner().getId().equals(applicant.getId())) {
            throw new OwnStudyApplicationException();
        }

        ensureCanJoin(studyId, applicant.getId());

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
    @Transactional(readOnly = true)
    public List<StudyApplicationResponse> getApplications(
            Long studyId,
            String username
    ) {

        Study study = studyRepository.findById(studyId)
                .orElseThrow(StudyNotFoundException::new);

        Long currentMemberId = memberRepository.findIdByUsername(username)
                .orElseThrow(MemberNotFoundException::new);

        if (!study.getOwner().getId().equals(currentMemberId)) {
            throw new StudyAccessDeniedException();
        }

        return applicationRepository
                .findByStudyIdOrderByCreatedAtAsc(studyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
    @Transactional
    public StudyApplicationResponse approve(
            Long studyId,
            Long applicationId,
            String username
    ) {

        Study study = studyRepository.findByIdForUpdate(studyId)
                .orElseThrow(StudyNotFoundException::new);

        Long currentMemberId = memberRepository.findIdByUsername(username)
                .orElseThrow(MemberNotFoundException::new);

        if (!study.getOwner().getId().equals(currentMemberId)) {
            throw new StudyAccessDeniedException();
        }

        StudyApplication application =
                applicationRepository.findByIdAndStudyId(
                        applicationId,
                        studyId
                ).orElseThrow(
                        StudyApplicationNotFoundException::new
                );

        long currentMemberCount =
                studyMemberRepository.countActiveMembers(studyId);

        if (currentMemberCount >= study.getMaxMembers()) {
            throw new StudyCapacityExceededException();
        }

        application.approve();
        StudyMember membership = studyMemberRepository.findByStudyIdAndMemberId(
                studyId, application.getApplicant().getId()).orElse(null);
        if (membership == null) {
            studyMemberRepository.save(new StudyMember(study, application.getApplicant()));
        } else {
            membership.activate();
        }

        long newMemberCount = currentMemberCount + 1;

        study.synchronizeStatusWithCapacity(newMemberCount);

        return toResponse(application);
    }
    @Transactional
    public StudyApplicationResponse reject(
            Long studyId,
            Long applicationId,
            String username
    ) {

        Study study = studyRepository.findByIdForUpdate(studyId)
                .orElseThrow(StudyNotFoundException::new);

        Long currentMemberId = memberRepository.findIdByUsername(username)
                .orElseThrow(MemberNotFoundException::new);

        if (!study.getOwner().getId().equals(currentMemberId)) {
            throw new StudyAccessDeniedException();
        }

        StudyApplication application =
                applicationRepository.findByIdAndStudyId(
                        applicationId,
                        studyId
                ).orElseThrow(
                        StudyApplicationNotFoundException::new
                );

        application.reject();

        return toResponse(application);
    }
    @Transactional
    public StudyApplicationResponse cancel(
            Long studyId,
            Long applicationId,
            String username
    ) {

        StudyApplication application =
                applicationRepository
                        .findByIdAndStudyIdAndApplicantUsername(
                                applicationId,
                                studyId,
                                username
                        )
                        .orElseThrow(
                                StudyApplicationNotFoundException::new
                        );

        application.cancel();

        return toResponse(application);
    }
    @Transactional
    public StudyApplicationResponse reapply(
            Long studyId,
            Long applicationId,
            String username
    ) {

        Study study = studyRepository.findByIdForUpdate(studyId)
                .orElseThrow(StudyNotFoundException::new);

        StudyApplication application =
                applicationRepository
                        .findByIdAndStudyIdAndApplicantUsername(
                                applicationId,
                                studyId,
                                username
                        )
                        .orElseThrow(
                                StudyApplicationNotFoundException::new
                        );

        StudyMember membership = ensureCanJoin(studyId, application.getApplicant().getId());

        if (study.getStatus() != StudyStatus.RECRUITING) {
            throw new StudyNotRecruitingException();
        }

        long currentMemberCount =
                studyMemberRepository.countActiveMembers(studyId);

        if (currentMemberCount >= study.getMaxMembers()) {
            throw new StudyCapacityExceededException();
        }

        if (membership != null && membership.getStatus() == StudyMemberStatus.LEFT
                && application.getStatus() == ApplicationStatus.APPROVED) {
            application.reapplyAfterLeaving();
        } else {
            application.reapply();
        }

        return toResponse(application);
    }


    private StudyMember ensureCanJoin(Long studyId, Long memberId) {
        StudyMember membership = studyMemberRepository.findByStudyIdAndMemberId(studyId, memberId).orElse(null);
        if (membership != null && membership.getStatus() == StudyMemberStatus.REMOVED) {
            throw new StudyMemberException(ErrorCode.STUDY_MEMBER_REMOVED);
        }
        if (membership != null && membership.getStatus() == StudyMemberStatus.ACTIVE) {
            throw new StudyMemberException(ErrorCode.STUDY_MEMBER_ALREADY_ACTIVE);
        }
        return membership;
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
