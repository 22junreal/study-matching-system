package com.studymatching.study.repository;

import com.studymatching.study.entity.Study;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.entity.StudyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyRepository extends JpaRepository<Study, Long> {

    Page<Study> findByOwnerUsername(
            String username,
            Pageable pageable
    );

    Page<Study> findByCategoryAndLevelAndStatus(
            StudyCategory category,
            StudyLevel level,
            StudyStatus status,
            Pageable pageable
    );
}