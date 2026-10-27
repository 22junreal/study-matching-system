package com.studymatching.profile.entity;
import com.studymatching.member.entity.Member;
import com.studymatching.study.entity.*;
import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.*;

@Entity
@Table(name = "profiles")
public class Profile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;
    @Column(length = 100) private String department;
    @Enumerated(EnumType.STRING) @Column(length = 50) private StudyCategory category;
    @Enumerated(EnumType.STRING) @Column(length = 50) private StudyLevel level;
    @ElementCollection
    @CollectionTable(name = "profile_days", joinColumns = @JoinColumn(name = "profile_id"))
    @Enumerated(EnumType.STRING) @Column(name = "day", length = 9, nullable = false)
    private Set<StudyDay> days = EnumSet.noneOf(StudyDay.class);
    private LocalTime startTime;
    private LocalTime endTime;
    protected Profile() { }
    public Profile(Member member) { this.member = member; }
    public void update(String department, StudyCategory category, StudyLevel level,
                       List<StudyDay> days, LocalTime startTime, LocalTime endTime) {
        StudySchedule.validateTime(startTime, endTime);
        this.days = StudySchedule.days(days);
        this.department = department;
        this.category = category;
        this.level = level;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getDepartment() { return department; }
    public StudyCategory getCategory() { return category; }
    public StudyLevel getLevel() { return level; }
    public List<StudyDay> getDays() { return days.stream().sorted().toList(); }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
