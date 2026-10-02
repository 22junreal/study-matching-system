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
import com.studymatching.studyapplication.exception.StudyCapacityExceededException;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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
    private List<Long> pendingApplicationIds;
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

        owner = memberRepository.save(owner);
        applicant1 = memberRepository.save(applicant1);

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

        pendingApplicationIds = new ArrayList<>();
        for (int i = 2; i <= 11; i++) {
            Member applicant = memberRepository.save(
                    new Member(
                            "applicant" + i,
                            "encoded-password",
                            "applicant" + i + "@test.com"
                    )
            );
            StudyApplication application = studyApplicationRepository.save(
                    new StudyApplication(study, applicant)
            );
            pendingApplicationIds.add(application.getId());
        }
    }

    @Test
    void 동시에_열_개의_신청을_승인해도_정원을_초과하지_않는다()
            throws Exception {
        int requestCount = pendingApplicationIds.size();
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);

        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> results = pendingApplicationIds.stream()
                .map(applicationId -> executorService.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    try {
                        studyApplicationService.approve(
                                studyId,
                                applicationId,
                                ownerUsername
                        );
                        return true;
                    } catch (StudyCapacityExceededException e) {
                        return false;
                    }
                }))
                .toList();

        try {
            assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<Boolean> outcomes = new ArrayList<>();
            for (Future<Boolean> result : results) {
                outcomes.add(result.get(10, TimeUnit.SECONDS));
            }

            assertThat(outcomes).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(true),
                                    java.util.stream.Stream.generate(() -> false).limit(9)
                            )
                            .toList()
            );
        } finally {
            executorService.shutdownNow();
        }

        long approvedCount =
                studyApplicationRepository.countByStudyIdAndStatus(
                        studyId,
                        ApplicationStatus.APPROVED
                );

        Study study =
                studyRepository.findById(studyId)
                        .orElseThrow();

        assertThat(approvedCount).isEqualTo(2);
        assertThat(study.getStatus()).isEqualTo(StudyStatus.CLOSED);
    }
}
