package com.studymatching.study.entity;

import com.studymatching.member.entity.Member;
import com.studymatching.study.exception.StudyInvalidTimeRangeException;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudyTest {

    private final Member owner = new Member(
            "owner",
            "password123",
            "owner@test.com"
    );

    @Test
    void studyWithOwnerOnlyCapacityStartsClosed() {
        Study study = createStudy(
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                1
        );

        assertThat(study.getStatus()).isEqualTo(StudyStatus.CLOSED);
    }

    @Test
    void statusFollowsCurrentMemberCountAndCapacity() {
        Study study = createStudy(
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                2
        );

        study.synchronizeStatusWithCapacity(2);
        assertThat(study.getStatus()).isEqualTo(StudyStatus.CLOSED);

        study.update(
                study.getTitle(),
                study.getDescription(),
                study.getCategory(),
                study.getLevel(),
                study.getDays(),
                study.getStartTime(),
                study.getEndTime(),
                3,
                2
        );
        assertThat(study.getStatus()).isEqualTo(StudyStatus.RECRUITING);
    }

    @Test
    void equalStartAndEndTimeIsRejected() {
        assertThatThrownBy(() -> createStudy(
                LocalTime.of(19, 0),
                LocalTime.of(19, 0),
                4
        )).isInstanceOf(StudyInvalidTimeRangeException.class);
    }

    @Test
    void startTimeAfterEndTimeIsRejected() {
        assertThatThrownBy(() -> createStudy(
                LocalTime.of(21, 0),
                LocalTime.of(19, 0),
                4
        )).isInstanceOf(StudyInvalidTimeRangeException.class);
    }

    private Study createStudy(
            LocalTime startTime,
            LocalTime endTime,
            int maxMembers
    ) {
        return new Study(
                owner,
                "Java Backend Study",
                "Spring Boot backend study",
                StudyCategory.PROGRAMMING,
                StudyLevel.BEGINNER,
                "MONDAY,WEDNESDAY",
                startTime,
                endTime,
                maxMembers
        );
    }
}
