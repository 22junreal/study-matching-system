package com.studymatching.profile.service;

import com.studymatching.member.entity.Member;
import com.studymatching.member.exception.MemberNotFoundException;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.profile.dto.ProfileResponse;
import com.studymatching.profile.dto.ProfileUpdateRequest;
import com.studymatching.profile.entity.Profile;
import com.studymatching.profile.repository.ProfileRepository;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.studymatching.profile.exception.ProfileNotFoundException;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class ProfileServiceTest {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private StudyApplicationRepository studyApplicationRepository;

    private Member member;

    @BeforeEach
    void setUp() {

        studyApplicationRepository.deleteAll();
        studyRepository.deleteAll();
        profileRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(
                new Member(
                        "testuser",
                        "password123",
                        "testuser@test.com"
                )
        );
    }

    @Test
    void updateProfileCreatesProfileWhenProfileDoesNotExist() {

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "AI",
                        "INTERMEDIATE",
                        "MONDAY,WEDNESDAY",
                        "19:00-21:00"
                );

        ProfileResponse response =
                profileService.updateMyProfile(
                        member.getUsername(),
                        request
                );

        assertThat(response.id())
                .isNotNull();

        assertThat(response.username())
                .isEqualTo("testuser");

        assertThat(response.department())
                .isEqualTo("인공지능학과");

        assertThat(response.preferredCategory())
                .isEqualTo("AI");

        assertThat(response.level())
                .isEqualTo("INTERMEDIATE");

        assertThat(response.availableDays())
                .isEqualTo("MONDAY,WEDNESDAY");

        assertThat(response.availableTime())
                .isEqualTo("19:00-21:00");

        assertThat(
                profileRepository.findByMemberUsername("testuser")
        ).isPresent();
    }

    @Test
    void getMyProfileSuccess() {

        Profile profile =
                new Profile(member);

        profile.update(
                "컴퓨터공학과",
                "PROGRAMMING",
                "BEGINNER",
                "TUESDAY,THURSDAY",
                "18:00-20:00"
        );

        profileRepository.save(profile);

        ProfileResponse response =
                profileService.getMyProfile(
                        member.getUsername()
                );

        assertThat(response.id())
                .isEqualTo(profile.getId());

        assertThat(response.username())
                .isEqualTo("testuser");

        assertThat(response.department())
                .isEqualTo("컴퓨터공학과");

        assertThat(response.preferredCategory())
                .isEqualTo("PROGRAMMING");

        assertThat(response.level())
                .isEqualTo("BEGINNER");

        assertThat(response.availableDays())
                .isEqualTo("TUESDAY,THURSDAY");

        assertThat(response.availableTime())
                .isEqualTo("18:00-20:00");
    }

    @Test
    void updateExistingProfileSuccess() {

        Profile profile =
                new Profile(member);

        profile.update(
                "컴퓨터공학과",
                "PROGRAMMING",
                "BEGINNER",
                "MONDAY",
                "18:00-20:00"
        );

        Profile savedProfile =
                profileRepository.save(profile);

        Long profileId = savedProfile.getId();

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "AI",
                        "ADVANCED",
                        "SATURDAY",
                        "14:00-17:00"
                );

        ProfileResponse response =
                profileService.updateMyProfile(
                        member.getUsername(),
                        request
                );

        assertThat(response.id())
                .isEqualTo(profileId);

        assertThat(response.department())
                .isEqualTo("인공지능학과");

        assertThat(response.preferredCategory())
                .isEqualTo("AI");

        assertThat(response.level())
                .isEqualTo("ADVANCED");

        assertThat(response.availableDays())
                .isEqualTo("SATURDAY");

        assertThat(response.availableTime())
                .isEqualTo("14:00-17:00");

        Profile updatedProfile =
                profileRepository.findById(profileId)
                        .orElseThrow();

        assertThat(updatedProfile.getDepartment())
                .isEqualTo("인공지능학과");

        assertThat(updatedProfile.getPreferredCategory())
                .isEqualTo("AI");

        assertThat(updatedProfile.getLevel())
                .isEqualTo("ADVANCED");

        assertThat(updatedProfile.getAvailableDays())
                .isEqualTo("SATURDAY");

        assertThat(updatedProfile.getAvailableTime())
                .isEqualTo("14:00-17:00");
    }

    @Test
    void updateProfileDoesNotCreateDuplicateProfile() {

        Profile profile =
                new Profile(member);

        profileRepository.save(profile);

        ProfileUpdateRequest firstRequest =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "AI",
                        "BEGINNER",
                        "MONDAY",
                        "19:00-21:00"
                );

        profileService.updateMyProfile(
                member.getUsername(),
                firstRequest
        );

        ProfileUpdateRequest secondRequest =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "DATA",
                        "INTERMEDIATE",
                        "WEDNESDAY",
                        "20:00-22:00"
                );

        profileService.updateMyProfile(
                member.getUsername(),
                secondRequest
        );

        assertThat(profileRepository.count())
                .isEqualTo(1);

        Profile savedProfile =
                profileRepository.findByMemberUsername(
                        member.getUsername()
                ).orElseThrow();

        assertThat(savedProfile.getPreferredCategory())
                .isEqualTo("DATA");

        assertThat(savedProfile.getLevel())
                .isEqualTo("INTERMEDIATE");
    }

    @Test
    void updateProfileFailsWhenMemberDoesNotExist() {

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        "AI",
                        "BEGINNER",
                        "MONDAY",
                        "19:00-21:00"
                );

        assertThatThrownBy(() ->
                profileService.updateMyProfile(
                        "unknownuser",
                        request
                )
        )
                .isInstanceOf(
                        MemberNotFoundException.class
                );
    }

    @Test
    void getProfileFailsWhenProfileDoesNotExist() {

        assertThatThrownBy(() ->
                profileService.getMyProfile(
                        member.getUsername()
                )
        )
                .isInstanceOf(
                        ProfileNotFoundException.class
                )
                .hasMessage(
                        "프로필이 존재하지 않습니다."
                );
    }
}