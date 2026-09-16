package com.studymatching.studyapplication.service;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.entity.Study;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.entity.StudyStatus;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.entity.ApplicationStatus;
import com.studymatching.studyapplication.entity.StudyApplication;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.context.ActiveProfiles;
import com.studymatching.support.PostgresTestContainerConfig;
import org.springframework.context.annotation.Import;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class StudyApplicationConcurrencyTest {


    @Autowired
    private StudyApplicationService studyApplicationService;

    @Autowired
    private StudyApplicationRepository studyApplicationRepository;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long studyId;
    private Long application1Id;
    private Long application2Id;
    private String ownerUsername;

    @BeforeEach
    void setUp() {
        studyApplicationRepository.deleteAll();
        studyRepository.deleteAll();
        memberRepository.deleteAll();

        Member owner = new Member(
                "owner",
                "encoded-password",
                "owner@test.com"
        );

        Member applicant1 = new Member(
                "applicant1",
                "encoded-password",
                "applicant1@test.com"
        );

        Member applicant2 = new Member(
                "applicant2",
                "encoded-password",
                "applicant2@test.com"
        );

        Member applicant3 = new Member(
                "applicant3",
                "encoded-password",
                "applicant3@test.com"
        );

        owner = memberRepository.save(owner);
        applicant1 = memberRepository.save(applicant1);
        applicant2 = memberRepository.save(applicant2);
        applicant3 = memberRepository.save(applicant3);

        ownerUsername = owner.getUsername();

        Study study = new Study(
                owner,
                "동시성 테스트 스터디",
                "동시 승인 테스트",
                StudyCategory.PROGRAMMING,
                StudyLevel.BEGINNER,
                "MON,WED",
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                3
        );

        study = studyRepository.save(study);
        studyId = study.getId();

        StudyApplication approvedApplication =
                new StudyApplication(study, applicant1);
        approvedApplication.approve();
        studyApplicationRepository.save(approvedApplication);

        StudyApplication application1 =
                studyApplicationRepository.save(
                        new StudyApplication(study, applicant2)
                );

        StudyApplication application2 =
                studyApplicationRepository.save(
                        new StudyApplication(study, applicant3)
                );

        application1Id = application1.getId();
        application2Id = application2.getId();
    }

    @Test
    void 동시에_두_신청을_승인해도_정원을_초과하지_않는다()
            throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Runnable approve1 = () -> {
            readyLatch.countDown();

            try {
                startLatch.await();

                studyApplicationService.approve(
                        studyId,
                        application1Id,
                        ownerUsername
                );

                successCount.incrementAndGet();

            } catch (Exception e) {
                failureCount.incrementAndGet();

                System.out.println(
                        "application1 실패: "
                                + e.getClass().getSimpleName()
                );
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable approve2 = () -> {
            readyLatch.countDown();

            try {
                startLatch.await();

                studyApplicationService.approve(
                        studyId,
                        application2Id,
                        ownerUsername
                );

                successCount.incrementAndGet();

            } catch (Exception e) {
                failureCount.incrementAndGet();

                System.out.println(
                        "application2 실패: "
                                + e.getClass().getSimpleName()
                );
            } finally {
                doneLatch.countDown();
            }
        };

        executorService.submit(approve1);
        executorService.submit(approve2);

        readyLatch.await();

        startLatch.countDown();

        doneLatch.await();

        executorService.shutdown();

        long approvedCount =
                studyApplicationRepository.countByStudyIdAndStatus(
                        studyId,
                        ApplicationStatus.APPROVED
                );

        Study study =
                studyRepository.findById(studyId)
                        .orElseThrow();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        assertThat(approvedCount).isEqualTo(2);
        assertThat(study.getStatus()).isEqualTo(StudyStatus.CLOSED);
    }
}