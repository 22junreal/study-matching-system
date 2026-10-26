package com.studymatching.studymember.entity;

import com.studymatching.common.entity.BaseTimeEntity;
import com.studymatching.common.exception.ErrorCode;
import com.studymatching.member.entity.Member;
import com.studymatching.study.entity.Study;
import com.studymatching.studymember.exception.StudyMemberException;
import jakarta.persistence.*;

@Entity
@Table(name = "study_members", uniqueConstraints = @UniqueConstraint(
        name = "uk_study_members_study_member", columnNames = {"study_id", "member_id"}))
public class StudyMember extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "study_id", nullable = false)
    private Study study;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private StudyMemberStatus status;

    protected StudyMember() { }

    public StudyMember(Study study, Member member) {
        this.study = study;
        this.member = member;
        this.status = StudyMemberStatus.ACTIVE;
    }

    public void leave() { requireActive(); status = StudyMemberStatus.LEFT; }
    public void remove() { requireActive(); status = StudyMemberStatus.REMOVED; }

    public void activate() {
        if (status == StudyMemberStatus.REMOVED) {
            throw new StudyMemberException(ErrorCode.STUDY_MEMBER_REMOVED);
        }
        if (status == StudyMemberStatus.ACTIVE) {
            throw new StudyMemberException(ErrorCode.STUDY_MEMBER_ALREADY_ACTIVE);
        }
        status = StudyMemberStatus.ACTIVE;
    }

    private void requireActive() {
        if (status != StudyMemberStatus.ACTIVE) {
            throw new StudyMemberException(ErrorCode.STUDY_MEMBER_NOT_ACTIVE);
        }
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public StudyMemberStatus getStatus() { return status; }
}
