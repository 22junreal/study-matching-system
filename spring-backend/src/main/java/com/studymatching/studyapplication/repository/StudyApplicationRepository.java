package com.studymatching.studyapplication.repository;

import com.studymatching.studyapplication.entity.StudyApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyApplicationRepository
        extends JpaRepository<StudyApplication, Long> {

    boolean existsByStudyIdAndApplicantId(
            Long studyId,
            Long applicantId
    );

    List<StudyApplication> findByStudyIdOrderByCreatedAtAsc(
            Long studyId
    );

    Optional<StudyApplication> findByIdAndStudyId(
            Long applicationId,
            Long studyId
    );
}