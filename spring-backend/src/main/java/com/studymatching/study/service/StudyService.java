package com.studymatching.study.service;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.dto.StudyCreateRequest;
import com.studymatching.study.dto.StudyResponse;
import com.studymatching.study.entity.Study;
import com.studymatching.study.exception.StudyNotFoundException;
import com.studymatching.study.repository.StudyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.studymatching.study.dto.StudyUpdateRequest;
import com.studymatching.study.exception.StudyAccessDeniedException;
import com.studymatching.study.dto.StudyPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.entity.StudyStatus;
import com.studymatching.member.exception.MemberNotFoundException;
import com.studymatching.study.repository.StudySpecification;
import org.springframework.data.jpa.domain.Specification;
import com.studymatching.study.exception.StudyCapacityBelowCurrentMembersException;
import com.studymatching.studyapplication.entity.ApplicationStatus;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;

import java.util.List;

@Service
public class StudyService {

    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;
    private final StudyApplicationRepository studyApplicationRepository;

    public StudyService(
            StudyRepository studyRepository,
            MemberRepository memberRepository,
            StudyApplicationRepository studyApplicationRepository
    ) {
        this.studyRepository = studyRepository;
        this.memberRepository = memberRepository;
        this.studyApplicationRepository = studyApplicationRepository;
    }

    @Transactional
    public StudyResponse createStudy(
            String username,
            StudyCreateRequest request
    ) {

        Member owner = memberRepository.findByUsername(username)
                .orElseThrow(MemberNotFoundException::new);

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

    @Transactional(readOnly = true)
    public StudyResponse getStudy(Long studyId) {

        Study study = studyRepository.findById(studyId)
                .orElseThrow(StudyNotFoundException::new);

        return toResponse(study);
    }

    @Transactional(readOnly = true)
    public StudyPageResponse getStudies(
            int page,
            int size
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Study> studyPage =
                studyRepository.findAll(pageable);

        return toPageResponse(studyPage);
    }
    @Transactional(readOnly = true)
    public StudyPageResponse searchStudies(
            StudyCategory category,
            StudyLevel level,
            StudyStatus status,
            int page,
            int size
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<Study> specification =
                Specification
                        .where(
                                StudySpecification.categoryEquals(
                                        category
                                )
                        )
                        .and(
                                StudySpecification.levelEquals(
                                        level
                                )
                        )
                        .and(
                                StudySpecification.statusEquals(
                                        status
                                )
                        );

        Page<Study> studyPage =
                studyRepository.findAll(
                        specification,
                        pageable
                );

        return toPageResponse(studyPage);
    }

    @Transactional(readOnly = true)
    public StudyPageResponse getMyStudies(
            String username,
            int page,
            int size
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Study> studyPage =
                studyRepository.findByOwnerUsername(
                        username,
                        pageable
                );

        return toPageResponse(studyPage);
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
    private StudyPageResponse toPageResponse(
            Page<Study> studyPage
    ) {

        return new StudyPageResponse(
                studyPage.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),

                studyPage.getNumber(),
                studyPage.getSize(),
                studyPage.getTotalElements(),
                studyPage.getTotalPages(),
                studyPage.isFirst(),
                studyPage.isLast()
        );
    }

    @Transactional
    public StudyResponse updateStudy(
            Long studyId,
            String username,
            StudyUpdateRequest request
    ) {
        Study study = studyRepository.findByIdForUpdate(studyId)
                .orElseThrow(StudyNotFoundException::new);

        if (!study.getOwner().getUsername().equals(username)) {
            throw new StudyAccessDeniedException();
        }

        long approvedCount =
                studyApplicationRepository.countByStudyIdAndStatus(
                        studyId,
                        ApplicationStatus.APPROVED
                );

        long currentMemberCount = approvedCount + 1;

        if (request.maxMembers() < currentMemberCount) {
            throw new StudyCapacityBelowCurrentMembersException();
        }

        study.update(
                request.title(),
                request.description(),
                request.category(),
                request.level(),
                request.days(),
                request.startTime(),
                request.endTime(),
                request.maxMembers()
        );

        if (request.maxMembers() == currentMemberCount) {
            study.close();
        }

        return toResponse(study);
    }
    @Transactional
    public void deleteStudy(
            Long studyId,
            String username
    ) {

        Study study = studyRepository.findById(studyId)
                .orElseThrow(StudyNotFoundException::new);

        if (!study.getOwner().getUsername().equals(username)) {
            throw new StudyAccessDeniedException();
        }

        studyRepository.delete(study);
    }
}