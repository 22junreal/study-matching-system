package com.studymatching.studyapplication.entity;

import com.studymatching.member.entity.Member;
import com.studymatching.study.entity.Study;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.studyapplication.exception.StudyApplicationAlreadyProcessedException;
import com.studymatching.studyapplication.exception.StudyApplicationCannotCancelException;
import com.studymatching.studyapplication.exception.StudyApplicationCannotReapplyException;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudyApplicationTest {

    @Test
    void pendingApplicationCanBeApproved() {
        StudyApplication application = createApplication();

        application.approve();

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void processedApplicationCannotBeApprovedAgain() {
        StudyApplication application = createApplication();
        application.approve();

        assertThatThrownBy(application::approve)
                .isInstanceOf(StudyApplicationAlreadyProcessedException.class);
    }

    @Test
    void processedApplicationCannotBeRejected() {
        StudyApplication application = createApplication();
        application.approve();

        assertThatThrownBy(application::reject)
                .isInstanceOf(StudyApplicationAlreadyProcessedException.class);
    }

    @Test
    void onlyPendingApplicationCanBeCanceled() {
        StudyApplication application = createApplication();
        application.reject();

        assertThatThrownBy(application::cancel)
                .isInstanceOf(StudyApplicationCannotCancelException.class);
    }

    @Test
    void onlyCanceledApplicationCanBeReapplied() {
        StudyApplication application = createApplication();

        assertThatThrownBy(application::reapply)
                .isInstanceOf(StudyApplicationCannotReapplyException.class);

        application.cancel();
        application.reapply();

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    private StudyApplication createApplication() {
        Member owner = new Member("owner", "password123", "owner@test.com");
        Member applicant = new Member("applicant", "password123", "applicant@test.com");
        Study study = new Study(
                owner,
                "Java Study",
                "Java backend study",
                StudyCategory.PROGRAMMING,
                StudyLevel.BEGINNER,
                "MONDAY,WEDNESDAY",
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                4
        );

        return new StudyApplication(study, applicant);
    }
}
