package com.studymatching.profile.entity;

import com.studymatching.member.entity.Member;
import jakarta.persistence.*;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(length = 100)
    private String department;

    @Column(length = 50)
    private String preferredCategory;

    @Column(length = 50)
    private String level;

    @Column(length = 100)
    private String availableDays;

    @Column(length = 100)
    private String availableTime;

    protected Profile() {
    }

    public Profile(Member member) {
        this.member = member;
    }

    public void update(
            String department,
            String preferredCategory,
            String level,
            String availableDays,
            String availableTime
    ) {
        this.department = department;
        this.preferredCategory = preferredCategory;
        this.level = level;
        this.availableDays = availableDays;
        this.availableTime = availableTime;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public String getDepartment() {
        return department;
    }

    public String getPreferredCategory() {
        return preferredCategory;
    }

    public String getLevel() {
        return level;
    }

    public String getAvailableDays() {
        return availableDays;
    }

    public String getAvailableTime() {
        return availableTime;
    }
}