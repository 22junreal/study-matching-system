package com.studymatching.study.entity;

import com.studymatching.member.entity.Member;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "studies")
public class Study {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyLevel level;

    @Column(length = 100)
    private String days;

    private LocalTime startTime;

    private LocalTime endTime;

    @Column(nullable = false)
    private Integer maxMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Study() {
    }

    public Study(
            Member owner,
            String title,
            String description,
            StudyCategory category,
            StudyLevel level,
            String days,
            LocalTime startTime,
            LocalTime endTime,
            Integer maxMembers
    ) {
        this.owner = owner;
        this.title = title;
        this.description = description;
        this.category = category;
        this.level = level;
        this.days = days;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxMembers = maxMembers;
        this.status = StudyStatus.RECRUITING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    public void update(
            String title,
            String description,
            StudyCategory category,
            StudyLevel level,
            String days,
            LocalTime startTime,
            LocalTime endTime,
            Integer maxMembers
    ) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.level = level;
        this.days = days;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxMembers = maxMembers;
        this.updatedAt = LocalDateTime.now();
    }
    public void close() {
        this.status = StudyStatus.CLOSED;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Member getOwner() {
        return owner;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public StudyCategory getCategory() {
        return category;
    }

    public StudyLevel getLevel() {
        return level;
    }

    public String getDays() {
        return days;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getMaxMembers() {
        return maxMembers;
    }

    public StudyStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}