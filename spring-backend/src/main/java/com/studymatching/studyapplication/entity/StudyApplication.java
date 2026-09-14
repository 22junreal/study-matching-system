package com.studymatching.studyapplication.entity;

import com.studymatching.member.entity.Member;
import com.studymatching.study.entity.Study;
import jakarta.persistence.*;

import java.time.LocalDateTime;

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
public class StudyApplication {

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

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StudyApplication() {
    }

    public StudyApplication(
            Study study,
            Member applicant
    ) {
        this.study = study;
        this.applicant = applicant;
        this.status = ApplicationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    public void approve() {
        this.status = ApplicationStatus.APPROVED;
        this.updatedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = ApplicationStatus.REJECTED;
        this.updatedAt = LocalDateTime.now();
    }
    public void cancel() {
        this.status = ApplicationStatus.CANCELED;
        this.updatedAt = LocalDateTime.now();
    }

    public void reapply() {
        this.status = ApplicationStatus.PENDING;
        this.updatedAt = LocalDateTime.now();
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}