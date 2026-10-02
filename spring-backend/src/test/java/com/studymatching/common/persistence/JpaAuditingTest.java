package com.studymatching.common.persistence;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.entity.Study;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.entity.StudyApplication;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import com.studymatching.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class JpaAuditingTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private StudyApplicationRepository applicationRepository;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        studyRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void createdAndUpdatedTimeAreSetWhenEntityIsPersisted() {
        Member member = memberRepository.saveAndFlush(
                new Member("member", "password", "member@test.com")
        );

        assertThat(member.getCreatedAt()).isNotNull();
        assertThat(member.getUpdatedAt()).isNotNull();
    }

    @Test
    void updatedTimeChangesWhenApplicationStateChanges() throws InterruptedException {
        Member owner = memberRepository.save(
                new Member("owner", "password", "owner@test.com")
        );
        Member applicant = memberRepository.save(
                new Member("applicant", "password", "applicant@test.com")
        );
        Study study = studyRepository.save(
                new Study(
                        owner,
                        "Java Study",
                        "Backend study",
                        StudyCategory.PROGRAMMING,
                        StudyLevel.BEGINNER,
                        "MONDAY",
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        4
                )
        );
        StudyApplication application = applicationRepository.saveAndFlush(
                new StudyApplication(study, applicant)
        );
        var initialUpdatedAt = application.getUpdatedAt();

        Thread.sleep(5);
        application.approve();
        StudyApplication updated = applicationRepository.saveAndFlush(application);

        assertThat(updated.getUpdatedAt()).isAfter(initialUpdatedAt);
    }
}
