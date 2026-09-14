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
import com.studymatching.studyapplication.entity.ApplicationStatus;
import com.studymatching.studyapplication.exception.StudyApplicationAlreadyProcessedException;
import com.studymatching.studyapplication.exception.StudyApplicationNotFoundException;
import com.studymatching.member.exception.MemberNotFoundException;
import com.studymatching.studyapplication.exception.StudyCapacityExceededException;

import com.studymatching.studyapplication.exception.StudyApplicationCannotCancelException;
import com.studymatching.studyapplication.exception.StudyApplicationCannotReapplyException;

import java.util.List;
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
                .orElseThrow(MemberNotFoundException::new);

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
    @Transactional(readOnly = true)
    public List<StudyApplicationResponse> getApplications(
            Long studyId,
            String username
    ) {

        Study study = studyRepository.findById(studyId)
                .orElseThrow(StudyNotFoundException::new);

        if (!study.getOwner().getUsername().equals(username)) {
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

        if (!study.getOwner().getUsername().equals(username)) {
            throw new StudyAccessDeniedException();
        }

        StudyApplication application =
                applicationRepository.findByIdAndStudyId(
                        applicationId,
                        studyId
                ).orElseThrow(
                        StudyApplicationNotFoundException::new
                );

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new StudyApplicationAlreadyProcessedException();
        }

        long approvedCount =
                applicationRepository.countByStudyIdAndStatus(
                        studyId,
                        ApplicationStatus.APPROVED
                );

        long currentMemberCount = approvedCount + 1;

        if (currentMemberCount >= study.getMaxMembers()) {
            throw new StudyCapacityExceededException();
        }

        application.approve();

        long newMemberCount = currentMemberCount + 1;

        if (newMemberCount >= study.getMaxMembers()) {
            study.close();
        }

        return toResponse(application);
    }
    @Transactional
    public StudyApplicationResponse reject(
            Long studyId,
            Long applicationId,
            String username
    ) {

        Study study = studyRepository.findById(studyId)
                .orElseThrow(StudyNotFoundException::new);

        if (!study.getOwner().getUsername().equals(username)) {
            throw new StudyAccessDeniedException();
        }

        StudyApplication application =
                applicationRepository.findByIdAndStudyId(
                        applicationId,
                        studyId
                ).orElseThrow(
                        StudyApplicationNotFoundException::new
                );

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new StudyApplicationAlreadyProcessedException();
        }

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

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new StudyApplicationCannotCancelException();
        }

        application.cancel();

        return toResponse(application);
    }
    @Transactional
    public StudyApplicationResponse reapply(
            Long studyId,
            Long applicationId,
            String username
    ) {

        Study study = studyRepository.findById(studyId)
                .orElseThrow(StudyNotFoundException::new);

        if (study.getStatus() != StudyStatus.RECRUITING) {
            throw new StudyNotRecruitingException();
        }

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

        if (application.getStatus() != ApplicationStatus.CANCELED) {
            throw new StudyApplicationCannotReapplyException();
        }

        long approvedCount =
                applicationRepository.countByStudyIdAndStatus(
                        studyId,
                        ApplicationStatus.APPROVED
                );

        long currentMemberCount = approvedCount + 1;

        if (currentMemberCount >= study.getMaxMembers()) {
            throw new StudyCapacityExceededException();
        }

        application.reapply();

        return toResponse(application);
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