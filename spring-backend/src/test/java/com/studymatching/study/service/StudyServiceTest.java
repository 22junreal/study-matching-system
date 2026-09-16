package com.studymatching.study.service;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.dto.StudyCreateRequest;
import com.studymatching.study.dto.StudyResponse;
import com.studymatching.study.dto.StudyUpdateRequest;
import com.studymatching.study.entity.Study;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.entity.StudyStatus;
import com.studymatching.study.exception.StudyAccessDeniedException;
import com.studymatching.study.exception.StudyNotFoundException;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.studymatching.study.dto.StudyPageResponse;
import com.studymatching.support.PostgresTestContainerConfig;
import org.springframework.context.annotation.Import;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class StudyServiceTest {

    @Autowired
    private StudyService studyService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyApplicationRepository studyApplicationRepository;

    private Member owner;
    private Member otherMember;

    @BeforeEach
    void setUp() {

        studyApplicationRepository.deleteAll();
        studyRepository.deleteAll();
        memberRepository.deleteAll();

        owner = memberRepository.save(
                new Member(
                        "owner",
                        "password123",
                        "owner@test.com"
                )
        );

        otherMember = memberRepository.save(
                new Member(
                        "other",
                        "password123",
                        "other@test.com"
                )
        );
    }

    @Test
    void createStudySuccess() {

        StudyCreateRequest request =
                new StudyCreateRequest(
                        "Java Backend Study",
                        "Spring Boot backend study",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        "MONDAY,WEDNESDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                );

        StudyResponse response =
                studyService.createStudy(
                        owner.getUsername(),
                        request
                );

        assertThat(response.id()).isNotNull();

        assertThat(response.ownerId())
                .isEqualTo(owner.getId());

        assertThat(response.ownerUsername())
                .isEqualTo(owner.getUsername());

        assertThat(response.title())
                .isEqualTo("Java Backend Study");

        assertThat(response.description())
                .isEqualTo("Spring Boot backend study");

        assertThat(response.category())
                .isEqualTo(StudyCategory.PROGRAMMING);

        assertThat(response.level())
                .isEqualTo(StudyLevel.BEGINNER);

        assertThat(response.days())
                .isEqualTo("MONDAY,WEDNESDAY");

        assertThat(response.startTime())
                .isEqualTo(LocalTime.of(19, 0));

        assertThat(response.endTime())
                .isEqualTo(LocalTime.of(21, 0));

        assertThat(response.maxMembers())
                .isEqualTo(4);

        assertThat(response.status())
                .isEqualTo(StudyStatus.RECRUITING);

        Study savedStudy =
                studyRepository.findById(
                        response.id()
                ).orElseThrow();

        assertThat(savedStudy.getTitle())
                .isEqualTo("Java Backend Study");

        assertThat(savedStudy.getOwner().getId())
                .isEqualTo(owner.getId());

        assertThat(savedStudy.getStatus())
                .isEqualTo(StudyStatus.RECRUITING);

        assertThat(savedStudy.getMaxMembers())
                .isEqualTo(4);
    }

    @Test
    void getStudySuccess() {

        Study study =
                studyRepository.save(
                        createStudyEntity()
                );

        StudyResponse response =
                studyService.getStudy(
                        study.getId()
                );

        assertThat(response.id())
                .isEqualTo(study.getId());
        assertThat(response.title())
                .isEqualTo("Java Backend Study");
        assertThat(response.ownerUsername())
                .isEqualTo(owner.getUsername());
        assertThat(response.status())
                .isEqualTo(StudyStatus.RECRUITING);
    }

    @Test
    void updateStudySuccess() {

        Study study =
                studyRepository.save(
                        createStudyEntity()
                );

        StudyUpdateRequest request =
                new StudyUpdateRequest(
                        "Updated Study",
                        "Updated description",
                        StudyCategory.AI,
                        StudyLevel.INTERMEDIATE,
                        "TUESDAY,THURSDAY",
                        LocalTime.of(20, 0),
                        LocalTime.of(22, 0),
                        6
                );

        StudyResponse response =
                studyService.updateStudy(
                        study.getId(),
                        owner.getUsername(),
                        request
                );

        assertThat(response.title())
                .isEqualTo("Updated Study");
        assertThat(response.description())
                .isEqualTo("Updated description");
        assertThat(response.category())
                .isEqualTo(StudyCategory.AI);
        assertThat(response.level())
                .isEqualTo(StudyLevel.INTERMEDIATE);
        assertThat(response.days())
                .isEqualTo("TUESDAY,THURSDAY");
        assertThat(response.startTime())
                .isEqualTo(LocalTime.of(20, 0));
        assertThat(response.endTime())
                .isEqualTo(LocalTime.of(22, 0));
        assertThat(response.maxMembers())
                .isEqualTo(6);

        Study updatedStudy =
                studyRepository.findById(
                        study.getId()
                ).orElseThrow();

        assertThat(updatedStudy.getTitle())
                .isEqualTo("Updated Study");
        assertThat(updatedStudy.getDescription())
                .isEqualTo("Updated description");
        assertThat(updatedStudy.getCategory())
                .isEqualTo(StudyCategory.AI);
        assertThat(updatedStudy.getLevel())
                .isEqualTo(StudyLevel.INTERMEDIATE);
        assertThat(updatedStudy.getMaxMembers())
                .isEqualTo(6);
    }

    @Test
    void nonOwnerCannotUpdateStudy() {

        Study study =
                studyRepository.save(
                        createStudyEntity()
                );

        StudyUpdateRequest request =
                new StudyUpdateRequest(
                        "Illegal Update",
                        "Other member should not update",
                        StudyCategory.DATA,
                        StudyLevel.ADVANCED,
                        "FRIDAY",
                        LocalTime.of(18, 0),
                        LocalTime.of(20, 0),
                        5
                );

        assertThatThrownBy(() ->
                studyService.updateStudy(
                        study.getId(),
                        otherMember.getUsername(),
                        request
                )
        )
                .isInstanceOf(
                        StudyAccessDeniedException.class
                );
    }

    @Test
    void nonOwnerCannotDeleteStudy() {

        Study study =
                studyRepository.save(
                        createStudyEntity()
                );

        assertThatThrownBy(() ->
                studyService.deleteStudy(
                        study.getId(),
                        otherMember.getUsername()
                )
        )
                .isInstanceOf(
                        StudyAccessDeniedException.class
                );

        assertThat(
                studyRepository.existsById(
                        study.getId()
                )
        ).isTrue();
    }

    @Test
    void nonexistentStudyThrowsException() {

        Long nonexistentStudyId = 999999L;

        assertThatThrownBy(() ->
                studyService.getStudy(
                        nonexistentStudyId
                )
        )
                .isInstanceOf(
                        StudyNotFoundException.class
                );
    }

    @Test
    void ownerCanDeleteStudy() {

        Study study =
                studyRepository.save(
                        createStudyEntity()
                );

        Long studyId = study.getId();

        studyService.deleteStudy(
                studyId,
                owner.getUsername()
        );

        assertThat(
                studyRepository.findById(
                        studyId
                )
        ).isEmpty();
    }
    @Test
    void getStudiesPaginationSuccess() {

        studyRepository.save(
                new Study(
                        owner,
                        "Study 1",
                        "Description 1",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        "MONDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        studyRepository.save(
                new Study(
                        owner,
                        "Study 2",
                        "Description 2",
                        StudyCategory.AI,
                        StudyLevel.INTERMEDIATE,
                        "TUESDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        studyRepository.save(
                new Study(
                        owner,
                        "Study 3",
                        "Description 3",
                        StudyCategory.DATA,
                        StudyLevel.ADVANCED,
                        "WEDNESDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        StudyPageResponse response =
                studyService.getStudies(
                        0,
                        2
                );

        assertThat(response.content())
                .hasSize(2);

        assertThat(response.page())
                .isEqualTo(0);

        assertThat(response.size())
                .isEqualTo(2);

        assertThat(response.totalElements())
                .isEqualTo(3);

        assertThat(response.totalPages())
                .isEqualTo(2);

        assertThat(response.first())
                .isTrue();

        assertThat(response.last())
                .isFalse();
    }
    @Test
    void searchStudiesSuccess() {

        Study matchingStudy =
                studyRepository.save(
                        new Study(
                                owner,
                                "Programming Study",
                                "Matching study",
                                StudyCategory.PROGRAMMING,
                                StudyLevel.BEGINNER,
                                "MONDAY",
                                LocalTime.of(19, 0),
                                LocalTime.of(21, 0),
                                4
                        )
                );

        studyRepository.save(
                new Study(
                        owner,
                        "AI Study",
                        "Non matching study",
                        StudyCategory.AI,
                        StudyLevel.INTERMEDIATE,
                        "TUESDAY",
                        LocalTime.of(20, 0),
                        LocalTime.of(22, 0),
                        5
                )
        );

        StudyPageResponse response =
                studyService.searchStudies(
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        StudyStatus.RECRUITING,
                        0,
                        10
                );

        assertThat(response.content())
                .hasSize(1);

        assertThat(response.totalElements())
                .isEqualTo(1);

        assertThat(response.content().get(0).id())
                .isEqualTo(matchingStudy.getId());

        assertThat(response.content().get(0).category())
                .isEqualTo(StudyCategory.PROGRAMMING);

        assertThat(response.content().get(0).level())
                .isEqualTo(StudyLevel.BEGINNER);

        assertThat(response.content().get(0).status())
                .isEqualTo(StudyStatus.RECRUITING);
    }
    @Test
    void getMyStudiesSuccess() {

        studyRepository.save(
                new Study(
                        owner,
                        "Owner Study 1",
                        "Owner study",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        "MONDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        studyRepository.save(
                new Study(
                        owner,
                        "Owner Study 2",
                        "Owner study",
                        StudyCategory.AI,
                        StudyLevel.INTERMEDIATE,
                        "TUESDAY",
                        LocalTime.of(20, 0),
                        LocalTime.of(22, 0),
                        5
                )
        );

        studyRepository.save(
                new Study(
                        otherMember,
                        "Other Member Study",
                        "Other member study",
                        StudyCategory.DATA,
                        StudyLevel.ADVANCED,
                        "WEDNESDAY",
                        LocalTime.of(18, 0),
                        LocalTime.of(20, 0),
                        3
                )
        );

        StudyPageResponse response =
                studyService.getMyStudies(
                        owner.getUsername(),
                        0,
                        10
                );

        assertThat(response.content())
                .hasSize(2);

        assertThat(response.totalElements())
                .isEqualTo(2);

        assertThat(response.content())
                .allMatch(study ->
                        study.ownerUsername()
                                .equals(owner.getUsername())
                );
    }
    @Test
    void searchStudiesByCategoryOnly() {

        studyRepository.save(
                new Study(
                        owner,
                        "Programming Study",
                        "Programming",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        "MONDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        studyRepository.save(
                new Study(
                        owner,
                        "AI Study",
                        "AI",
                        StudyCategory.AI,
                        StudyLevel.BEGINNER,
                        "TUESDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        StudyPageResponse response =
                studyService.searchStudies(
                        StudyCategory.PROGRAMMING,
                        null,
                        null,
                        0,
                        10
                );

        assertThat(response.content())
                .hasSize(1);

        assertThat(response.content().get(0).category())
                .isEqualTo(StudyCategory.PROGRAMMING);
    }
    @Test
    void searchStudiesByLevelOnly() {

        studyRepository.save(
                new Study(
                        owner,
                        "Beginner Study",
                        "Beginner",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        "MONDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        studyRepository.save(
                new Study(
                        owner,
                        "Advanced Study",
                        "Advanced",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.ADVANCED,
                        "TUESDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        StudyPageResponse response =
                studyService.searchStudies(
                        null,
                        StudyLevel.ADVANCED,
                        null,
                        0,
                        10
                );

        assertThat(response.content())
                .hasSize(1);

        assertThat(response.content().get(0).level())
                .isEqualTo(StudyLevel.ADVANCED);
    }
    @Test
    void searchStudiesByStatusOnly() {

        Study recruitingStudy =
                studyRepository.save(
                        new Study(
                                owner,
                                "Recruiting Study",
                                "Recruiting",
                                StudyCategory.PROGRAMMING,
                                StudyLevel.BEGINNER,
                                "MONDAY",
                                LocalTime.of(19, 0),
                                LocalTime.of(21, 0),
                                4
                        )
                );

        Study closedStudy =
                studyRepository.save(
                        new Study(
                                owner,
                                "Closed Study",
                                "Closed",
                                StudyCategory.PROGRAMMING,
                                StudyLevel.BEGINNER,
                                "TUESDAY",
                                LocalTime.of(19, 0),
                                LocalTime.of(21, 0),
                                4
                        )
                );

        closedStudy.close();
        studyRepository.save(closedStudy);

        StudyPageResponse response =
                studyService.searchStudies(
                        null,
                        null,
                        StudyStatus.CLOSED,
                        0,
                        10
                );

        assertThat(response.content())
                .hasSize(1);

        assertThat(response.content().get(0).status())
                .isEqualTo(StudyStatus.CLOSED);
    }
    @Test
    void searchStudiesByCategoryAndLevel() {

        studyRepository.save(
                new Study(
                        owner,
                        "Matching Study",
                        "Match",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        "MONDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        studyRepository.save(
                new Study(
                        owner,
                        "Different Level",
                        "No match",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.ADVANCED,
                        "TUESDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );

        StudyPageResponse response =
                studyService.searchStudies(
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        null,
                        0,
                        10
                );

        assertThat(response.content())
                .hasSize(1);

        assertThat(response.content().get(0).title())
                .isEqualTo("Matching Study");
    }
    @Test
    void searchStudiesWithoutConditionsReturnsAll() {

        studyRepository.save(
                createStudyEntity()
        );

        studyRepository.save(
                new Study(
                        owner,
                        "Second Study",
                        "Second",
                        StudyCategory.AI,
                        StudyLevel.INTERMEDIATE,
                        "TUESDAY",
                        LocalTime.of(20, 0),
                        LocalTime.of(22, 0),
                        5
                )
        );

        StudyPageResponse response =
                studyService.searchStudies(
                        null,
                        null,
                        null,
                        0,
                        10
                );

        assertThat(response.totalElements())
                .isEqualTo(2);

        assertThat(response.content())
                .hasSize(2);
    }

    private Study createStudyEntity() {

        return new Study(
                owner,
                "Java Backend Study",
                "Spring Boot backend study",
                StudyCategory.PROGRAMMING,
                StudyLevel.BEGINNER,
                "MONDAY,WEDNESDAY",
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                4
        );
    }
}