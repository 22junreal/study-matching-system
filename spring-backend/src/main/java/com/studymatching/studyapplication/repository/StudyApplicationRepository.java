package com.studymatching.studyapplication.repository;

import com.studymatching.studyapplication.entity.StudyApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyApplicationRepository
        extends JpaRepository<StudyApplication, Long> {

    boolean existsByStudyIdAndApplicantId(
            Long studyId,
            Long applicantId
    );
}