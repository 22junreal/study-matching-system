package com.studymatching.studymember.repository;

import com.studymatching.studymember.entity.StudyMember;
import com.studymatching.studymember.entity.StudyMemberStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudyMemberRepository extends JpaRepository<StudyMember, Long> {
    @EntityGraph(attributePaths = "member")
    Optional<StudyMember> findByStudyIdAndMemberId(Long studyId, Long memberId);

    @EntityGraph(attributePaths = "member")
    List<StudyMember> findByStudyIdAndStatusOrderByIdAsc(Long studyId, StudyMemberStatus status);

    long countByStudyIdAndStatus(Long studyId, StudyMemberStatus status);

    default long countActiveMembers(Long studyId) {
        return countByStudyIdAndStatus(studyId, StudyMemberStatus.ACTIVE);
    }
}
