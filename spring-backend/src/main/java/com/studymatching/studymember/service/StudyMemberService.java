package com.studymatching.studymember.service;

import com.studymatching.common.exception.ErrorCode;
import com.studymatching.member.exception.MemberNotFoundException;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.entity.Study;
import com.studymatching.study.exception.StudyAccessDeniedException;
import com.studymatching.study.exception.StudyNotFoundException;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studymember.dto.StudyMemberResponse;
import com.studymatching.studymember.entity.StudyMember;
import com.studymatching.studymember.entity.StudyMemberStatus;
import com.studymatching.studymember.exception.StudyMemberException;
import com.studymatching.studymember.repository.StudyMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class StudyMemberService {
    private final StudyRepository studies;
    private final StudyMemberRepository memberships;
    private final MemberRepository members;

    public StudyMemberService(StudyRepository studies, StudyMemberRepository memberships, MemberRepository members) {
        this.studies = studies;
        this.memberships = memberships;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public List<StudyMemberResponse> list(Long studyId, String username) {
        Study study = studies.findById(studyId).orElseThrow(StudyNotFoundException::new);
        StudyMember requester = memberships.findByStudyIdAndMemberId(studyId, memberId(username))
                .orElseThrow(StudyAccessDeniedException::new);
        if (requester.getStatus() != StudyMemberStatus.ACTIVE) {
            throw new StudyAccessDeniedException();
        }
        return memberships.findByStudyIdAndStatusOrderByIdAsc(studyId, StudyMemberStatus.ACTIVE)
                .stream().map(membership -> response(study, membership)).toList();
    }

    @Transactional
    public void leave(Long studyId, String username) {
        Study study = lockStudy(studyId);
        Long memberId = memberId(username);
        if (study.getOwner().getId().equals(memberId)) {
            throw new StudyMemberException(ErrorCode.STUDY_OWNER_CANNOT_LEAVE);
        }
        active(studyId, memberId).leave();
        study.synchronizeStatusWithCapacity(memberships.countActiveMembers(studyId));
    }

    @Transactional
    public void remove(Long studyId, Long targetId, String username) {
        Study study = lockStudy(studyId);
        requireOwner(study, username);
        if (study.getOwner().getId().equals(targetId)) {
            throw new StudyMemberException(ErrorCode.STUDY_OWNER_CANNOT_LEAVE);
        }
        active(studyId, targetId).remove();
        study.synchronizeStatusWithCapacity(memberships.countActiveMembers(studyId));
    }

    @Transactional
    public StudyMemberResponse transfer(Long studyId, Long targetId, String username) {
        Study study = lockStudy(studyId);
        requireOwner(study, username);
        if (study.getOwner().getId().equals(targetId)) {
            throw new StudyMemberException(ErrorCode.STUDY_OWNER_TRANSFER_TO_SELF);
        }
        StudyMember target = active(studyId, targetId);
        // Ownership has one source of truth: studies.owner_id. Both memberships stay ACTIVE.
        study.transferOwnership(target.getMember());
        return response(study, target);
    }

    private Study lockStudy(Long studyId) {
        return studies.findByIdForUpdate(studyId).orElseThrow(StudyNotFoundException::new);
    }

    private Long memberId(String username) {
        return members.findIdByUsername(username).orElseThrow(MemberNotFoundException::new);
    }

    private void requireOwner(Study study, String username) {
        if (!study.getOwner().getId().equals(memberId(username))) {
            throw new StudyAccessDeniedException();
        }
    }

    private StudyMember active(Long studyId, Long memberId) {
        StudyMember membership = memberships.findByStudyIdAndMemberId(studyId, memberId)
                .orElseThrow(() -> new StudyMemberException(ErrorCode.STUDY_MEMBER_NOT_FOUND));
        if (membership.getStatus() != StudyMemberStatus.ACTIVE) {
            throw new StudyMemberException(ErrorCode.STUDY_MEMBER_NOT_ACTIVE);
        }
        return membership;
    }

    private StudyMemberResponse response(Study study, StudyMember membership) {
        return new StudyMemberResponse(membership.getMember().getId(), membership.getMember().getUsername(),
                study.getOwner().getId().equals(membership.getMember().getId()) ? "OWNER" : "MEMBER",
                membership.getStatus());
    }
}
