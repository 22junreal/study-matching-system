package com.studymatching.study.repository;

import com.studymatching.study.entity.Study;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudyRepository
        extends JpaRepository<Study, Long>,
        JpaSpecificationExecutor<Study> {

    Page<Study> findByOwnerUsername(
            String username,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from Study s
            where s.id = :studyId
            """)
    Optional<Study> findByIdForUpdate(
            @Param("studyId") Long studyId
    );
}