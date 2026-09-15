package com.studymatching.studyapplication.service;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.entity.Study;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.dto.StudyApplicationResponse;
import com.studymatching.studyapplication.entity.ApplicationStatus;
import com.studymatching.studyapplication.entity.StudyApplication;
import com.studymatching.studyapplication.exception.StudyApplicationCannotCancelException;
import com.studymatching.studyapplication.exception.StudyApplicationCannotReapplyException;
import com.studymatching.studyapplication.exception.StudyNotRecruitingException;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class StudyApplicationServiceTest {

    @Autowired
    private StudyApplicationService studyApplicationService;

    @Autowired
    private StudyApplicationRepository studyApplicationRepository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member owner;
    private Member applicant;
    private Study study;

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

        applicant = memberRepository.save(
                new Member(
                        "applicant",
                        "password123",
                        "applicant@test.com"
                )
        );

        study = studyRepository.save(
                new Study(
                        owner,
                        "Java Study",
                        "Java backend study",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        "MONDAY,WEDNESDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );
    }

    @Test
    void pendingApplicationCanBeCanceled() {

        StudyApplication application =
                studyApplicationRepository.save(
                        new StudyApplication(
                                study,
                                applicant
                        )
                );

        StudyApplicationResponse response =
                studyApplicationService.cancel(
                        study.getId(),
                        application.getId(),
                        applicant.getUsername()
                );

        assertThat(response.status())
                .isEqualTo(ApplicationStatus.CANCELED);

        StudyApplication savedApplication =
                studyApplicationRepository.findById(
                        application.getId()
                ).orElseThrow();

        assertThat(savedApplication.getStatus())
                .isEqualTo(ApplicationStatus.CANCELED);
    }

    @Test
    void canceledApplicationCanBeReapplied() {

        StudyApplication application =
                new StudyApplication(
                        study,
                        applicant
                );

        application.cancel();

        studyApplicationRepository.save(application);

        StudyApplicationResponse response =
                studyApplicationService.reapply(
                        study.getId(),
                        application.getId(),
                        applicant.getUsername()
                );

        assertThat(response.status())
                .isEqualTo(ApplicationStatus.PENDING);

        StudyApplication savedApplication =
                studyApplicationRepository.findById(
                        application.getId()
                ).orElseThrow();

        assertThat(savedApplication.getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    void approvedApplicationCannotBeCanceled() {

        StudyApplication application =
                new StudyApplication(
                        study,
                        applicant
                );

        application.approve();

        studyApplicationRepository.save(application);

        assertThatThrownBy(() ->
                studyApplicationService.cancel(
                        study.getId(),
                        application.getId(),
                        applicant.getUsername()
                )
        )
                .isInstanceOf(
                        StudyApplicationCannotCancelException.class
                );
    }

    @Test
    void rejectedApplicationCannotBeCanceled() {

        StudyApplication application =
                new StudyApplication(
                        study,
                        applicant
                );

        application.reject();

        studyApplicationRepository.save(application);

        assertThatThrownBy(() ->
                studyApplicationService.cancel(
                        study.getId(),
                        application.getId(),
                        applicant.getUsername()
                )
        )
                .isInstanceOf(
                        StudyApplicationCannotCancelException.class
                );
    }

    @Test
    void pendingApplicationCannotBeReapplied() {

        StudyApplication application =
                studyApplicationRepository.save(
                        new StudyApplication(
                                study,
                                applicant
                        )
                );

        assertThatThrownBy(() ->
                studyApplicationService.reapply(
                        study.getId(),
                        application.getId(),
                        applicant.getUsername()
                )
        )
                .isInstanceOf(
                        StudyApplicationCannotReapplyException.class
                );
    }

    @Test
    void canceledApplicationCannotBeReappliedWhenStudyIsClosed() {

        StudyApplication application =
                new StudyApplication(
                        study,
                        applicant
                );

        application.cancel();

        studyApplicationRepository.save(application);

        study.close();
        studyRepository.save(study);

        assertThatThrownBy(() ->
                studyApplicationService.reapply(
                        study.getId(),
                        application.getId(),
                        applicant.getUsername()
                )
        )
                .isInstanceOf(
                        StudyNotRecruitingException.class
                );
    }
}