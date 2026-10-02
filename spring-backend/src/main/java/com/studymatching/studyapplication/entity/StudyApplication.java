package com.studymatching.studyapplication.entity;

import com.studymatching.common.entity.BaseTimeEntity;
import com.studymatching.member.entity.Member;
import com.studymatching.study.entity.Study;
import com.studymatching.studyapplication.exception.StudyApplicationAlreadyProcessedException;
import com.studymatching.studyapplication.exception.StudyApplicationCannotCancelException;
import com.studymatching.studyapplication.exception.StudyApplicationCannotReapplyException;
import jakarta.persistence.*;


@Entity
@Table(
        name = "study_applications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_study_applications_study_applicant",
                        columnNames = {"study_id", "applicant_id"}
                )
        }
)
public class StudyApplication extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Member applicant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    protected StudyApplication() {
    }

    public StudyApplication(
            Study study,
            Member applicant
    ) {
        this.study = study;
        this.applicant = applicant;
        this.status = ApplicationStatus.PENDING;
    }
    public void approve() {
        validatePending();
        this.status = ApplicationStatus.APPROVED;
    }

    public void reject() {
        validatePending();
        this.status = ApplicationStatus.REJECTED;
    }
    public void cancel() {
        if (status != ApplicationStatus.PENDING) {
            throw new StudyApplicationCannotCancelException();
        }
        this.status = ApplicationStatus.CANCELED;
    }

    public void reapply() {
        if (status != ApplicationStatus.CANCELED) {
            throw new StudyApplicationCannotReapplyException();
        }
        this.status = ApplicationStatus.PENDING;
    }

    private void validatePending() {
        if (status != ApplicationStatus.PENDING) {
            throw new StudyApplicationAlreadyProcessedException();
        }
    }

    public Long getId() {
        return id;
    }

    public Study getStudy() {
        return study;
    }

    public Member getApplicant() {
        return applicant;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

}
