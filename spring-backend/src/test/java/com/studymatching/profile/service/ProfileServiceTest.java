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
import com.studymatching.support.PostgresTestContainerConfig;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class ProfileServiceTest {
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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
                        com.studymatching.study.entity.StudyCategory.AI,
                        com.studymatching.study.entity.StudyLevel.INTERMEDIATE,
                        java.util.List.of(com.studymatching.study.entity.StudyDay.MONDAY, com.studymatching.study.entity.StudyDay.WEDNESDAY),
                        java.time.LocalTime.of(19, 0), java.time.LocalTime.of(21, 0)
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

        assertThat(response.category())
                .isEqualTo(com.studymatching.study.entity.StudyCategory.AI);

        assertThat(response.level())
                .isEqualTo(com.studymatching.study.entity.StudyLevel.INTERMEDIATE);

        assertThat(response.days())
                .isEqualTo(java.util.List.of(com.studymatching.study.entity.StudyDay.MONDAY, com.studymatching.study.entity.StudyDay.WEDNESDAY));

        assertThat(response.startTime())
                .isEqualTo(java.time.LocalTime.of(19, 0));

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
                com.studymatching.study.entity.StudyCategory.PROGRAMMING,
                com.studymatching.study.entity.StudyLevel.BEGINNER,
                java.util.List.of(com.studymatching.study.entity.StudyDay.TUESDAY, com.studymatching.study.entity.StudyDay.THURSDAY),
                java.time.LocalTime.of(18, 0), java.time.LocalTime.of(20, 0)
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

        assertThat(response.category())
                .isEqualTo(com.studymatching.study.entity.StudyCategory.PROGRAMMING);

        assertThat(response.level())
                .isEqualTo(com.studymatching.study.entity.StudyLevel.BEGINNER);

        assertThat(response.days())
                .isEqualTo(java.util.List.of(com.studymatching.study.entity.StudyDay.TUESDAY, com.studymatching.study.entity.StudyDay.THURSDAY));

        assertThat(response.startTime())
                .isEqualTo(java.time.LocalTime.of(18, 0));
    }

    @Test
    void updateExistingProfileSuccess() {

        Profile profile =
                new Profile(member);

        profile.update(
                "컴퓨터공학과",
                com.studymatching.study.entity.StudyCategory.PROGRAMMING,
                com.studymatching.study.entity.StudyLevel.BEGINNER,
                java.util.List.of(com.studymatching.study.entity.StudyDay.MONDAY),
                java.time.LocalTime.of(18, 0), java.time.LocalTime.of(20, 0)
        );

        Profile savedProfile =
                profileRepository.save(profile);

        Long profileId = savedProfile.getId();

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        com.studymatching.study.entity.StudyCategory.AI,
                        com.studymatching.study.entity.StudyLevel.ADVANCED,
                        java.util.List.of(com.studymatching.study.entity.StudyDay.SATURDAY),
                        java.time.LocalTime.of(14, 0), java.time.LocalTime.of(17, 0)
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

        assertThat(response.category())
                .isEqualTo(com.studymatching.study.entity.StudyCategory.AI);

        assertThat(response.level())
                .isEqualTo(com.studymatching.study.entity.StudyLevel.ADVANCED);

        assertThat(response.days())
                .isEqualTo(java.util.List.of(com.studymatching.study.entity.StudyDay.SATURDAY));

        assertThat(response.startTime())
                .isEqualTo(java.time.LocalTime.of(14, 0));

        Profile updatedProfile =
                profileRepository.findById(profileId)
                        .orElseThrow();

        assertThat(updatedProfile.getDepartment())
                .isEqualTo("인공지능학과");

        assertThat(updatedProfile.getCategory())
                .isEqualTo(com.studymatching.study.entity.StudyCategory.AI);

        assertThat(updatedProfile.getLevel())
                .isEqualTo(com.studymatching.study.entity.StudyLevel.ADVANCED);

        assertThat(jdbc.queryForList("select day from profile_days where profile_id = ?", String.class, profileId))
                .containsExactly("SATURDAY");

        assertThat(updatedProfile.getStartTime())
                .isEqualTo(java.time.LocalTime.of(14, 0));
    }

    @Test
    void updateProfileDoesNotCreateDuplicateProfile() {

        Profile profile =
                new Profile(member);

        profileRepository.save(profile);

        ProfileUpdateRequest firstRequest =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        com.studymatching.study.entity.StudyCategory.AI,
                        com.studymatching.study.entity.StudyLevel.BEGINNER,
                        java.util.List.of(com.studymatching.study.entity.StudyDay.MONDAY),
                        java.time.LocalTime.of(19, 0), java.time.LocalTime.of(21, 0)
                );

        profileService.updateMyProfile(
                member.getUsername(),
                firstRequest
        );

        ProfileUpdateRequest secondRequest =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        com.studymatching.study.entity.StudyCategory.DATA,
                        com.studymatching.study.entity.StudyLevel.INTERMEDIATE,
                        java.util.List.of(com.studymatching.study.entity.StudyDay.WEDNESDAY),
                        java.time.LocalTime.of(20, 0), java.time.LocalTime.of(22, 0)
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

        assertThat(savedProfile.getCategory())
                .isEqualTo(com.studymatching.study.entity.StudyCategory.DATA);

        assertThat(savedProfile.getLevel())
                .isEqualTo(com.studymatching.study.entity.StudyLevel.INTERMEDIATE);
    }

    @Test
    void updateProfileFailsWhenMemberDoesNotExist() {

        ProfileUpdateRequest request =
                new ProfileUpdateRequest(
                        "인공지능학과",
                        com.studymatching.study.entity.StudyCategory.AI,
                        com.studymatching.study.entity.StudyLevel.BEGINNER,
                        java.util.List.of(com.studymatching.study.entity.StudyDay.MONDAY),
                        java.time.LocalTime.of(19, 0), java.time.LocalTime.of(21, 0)
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