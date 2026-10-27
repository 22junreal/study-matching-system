package com.studymatching.study.entity;

import com.studymatching.common.entity.BaseTimeEntity;
import com.studymatching.member.entity.Member;
import com.studymatching.study.exception.StudyInvalidTimeRangeException;
import jakarta.persistence.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "studies")
public class Study extends BaseTimeEntity {

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

    @ElementCollection
    @CollectionTable(name = "study_days", joinColumns = @JoinColumn(name = "study_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day", nullable = false, length = 9)
    @BatchSize(size = 50)
    private Set<StudyDay> days;

    private LocalTime startTime;

    private LocalTime endTime;

    @Column(nullable = false)
    private Integer maxMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyStatus status;

    protected Study() {
    }

    public Study(
            Member owner,
            String title,
            String description,
            StudyCategory category,
            StudyLevel level,
            List<StudyDay> days,
            LocalTime startTime,
            LocalTime endTime,
            Integer maxMembers
    ) {
        validateTimeRange(startTime, endTime);
        this.owner = owner;
        this.title = title;
        this.description = description;
        this.category = category;
        this.level = level;
        this.days = StudySchedule.days(days);
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxMembers = maxMembers;
        this.status = Integer.valueOf(1).equals(maxMembers)
                ? StudyStatus.CLOSED
                : StudyStatus.RECRUITING;
    }
    public void update(
            String title,
            String description,
            StudyCategory category,
            StudyLevel level,
            List<StudyDay> days,
            LocalTime startTime,
            LocalTime endTime,
            Integer maxMembers,
            long currentMemberCount
    ) {
        validateTimeRange(startTime, endTime);
        this.title = title;
        this.description = description;
        this.category = category;
        this.level = level;
        this.days = StudySchedule.days(days);
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxMembers = maxMembers;
        synchronizeStatusWithCapacity(currentMemberCount);
    }

    public void synchronizeStatusWithCapacity(long currentMemberCount) {
        this.status = currentMemberCount >= maxMembers
                ? StudyStatus.CLOSED
                : StudyStatus.RECRUITING;
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        StudySchedule.validateTime(startTime, endTime);
    }

    public void transferOwnership(Member newOwner) {
        this.owner = java.util.Objects.requireNonNull(newOwner);
    }

    public void close() {
        this.status = StudyStatus.CLOSED;
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

    public List<StudyDay> getDays() {
        return days.stream().sorted().toList();
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

}
