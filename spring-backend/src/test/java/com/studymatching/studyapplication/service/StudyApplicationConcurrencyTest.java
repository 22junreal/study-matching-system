package com.studymatching.studyapplication.service;

import com.studymatching.studymember.entity.StudyMember;
import com.studymatching.studymember.repository.StudyMemberRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import java.util.concurrent.ExecutionException;
import org.springframework.dao.OptimisticLockingFailureException;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class StudyApplicationConcurrencyTest {


    @Autowired
    private StudyMemberRepository memberships;

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
                java.util.List.of(com.studymatching.study.entity.StudyDay.MONDAY, com.studymatching.study.entity.StudyDay.WEDNESDAY),
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                3
        );

        study = studyRepository.save(study);
        studyId = study.getId();
        memberships.save(new StudyMember(study, owner));
        memberships.save(new StudyMember(study, applicant1));

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

    @ParameterizedTest(name = "{0} commits before {1}")
    @CsvSource({
            "approve,cancel", "cancel,approve", "cancel,reject", "reject,cancel"
    })
    void staleStateTransitionMustConflict(String winner, String loser) throws Exception {
        Long applicationId = pendingApplicationIds.getFirst();
        if (winner.equals("reapply")) {
            studyApplicationService.cancel(studyId, applicationId, "applicant2");
        }
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch winnerCommitted = new CountDownLatch(1);
        ThreadLocal<Boolean> losingThread = ThreadLocal.withInitial(() -> false);
        MethodInterceptor synchronizeReads = invocation -> {
            Object loaded = invocation.proceed();
            String method = invocation.getMethod().getName();
            if (!method.equals("findByIdAndStudyId")
                    && !method.equals("findByIdAndStudyIdAndApplicantUsername")) {
                return loaded;
            }
            bothRead.countDown();
            if (!bothRead.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Both transactions must read the same initial state");
            }
            if (losingThread.get() && !winnerCommitted.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Winning transaction must commit");
            }
            return loaded;
        };
        // Observe actual repository reads without replacing the query or transaction.
        Advised repositoryProxy =
                (Advised) studyApplicationRepository;
        repositoryProxy.addAdvice(0, synchronizeReads);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                try {
                    transition(winner, applicationId);
                } finally {
                    winnerCommitted.countDown();
                }
            });
            Future<?> second = executor.submit(() -> {
                losingThread.set(true);
                try {
                    transition(loser, applicationId);
                } finally {
                    losingThread.remove();
                }
            });
            first.get(15, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                repositoryProxy.removeAdvice(synchronizeReads);
            }
        }

        ApplicationStatus expected = switch (winner) {
            case "approve" -> ApplicationStatus.APPROVED;
            case "cancel" -> ApplicationStatus.CANCELED;
            case "reject" -> ApplicationStatus.REJECTED;
            case "reapply" -> ApplicationStatus.PENDING;
            default -> throw new AssertionError(winner);
        };
        assertThat(studyApplicationRepository.findById(applicationId).orElseThrow().getStatus())
                .isEqualTo(expected);
        assertThat(memberships.countActiveMembers(studyId))
                .isEqualTo(winner.equals("approve") ? 3 : 2);
        // In particular, a losing approval must roll back the Study's CLOSED state too.
        assertThat(studyRepository.findById(studyId).orElseThrow().getStatus())
                .isEqualTo(winner.equals("approve") ? StudyStatus.CLOSED : StudyStatus.RECRUITING);
    }

    @ParameterizedTest
    @CsvSource({"approve,reject", "reapply,reapply"})
    void studyLockedTransitionsSerialize(String firstOperation, String secondOperation) throws Exception {
        Long applicationId = pendingApplicationIds.getFirst();
        if (firstOperation.equals("reapply")) {
            studyApplicationService.cancel(studyId, applicationId, "applicant2");
        }
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> lockedOutcome(firstOperation, applicationId, start));
            var second = executor.submit(() -> lockedOutcome(secondOperation, applicationId, start));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", firstOperation.equals("reapply")
                            ? "STUDY_APPLICATION_CANNOT_REAPPLY" : "STUDY_APPLICATION_ALREADY_PROCESSED");
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        ApplicationStatus status = studyApplicationRepository.findById(applicationId).orElseThrow().getStatus();
        if (firstOperation.equals("reapply")) {
            assertThat(status).isEqualTo(ApplicationStatus.PENDING);
        } else {
            assertThat(status).isIn(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED);
        }
        assertThat(memberships.countActiveMembers(studyId)).isEqualTo(status == ApplicationStatus.APPROVED ? 3 : 2);
        assertThat(studyRepository.findById(studyId).orElseThrow().getStatus())
                .isEqualTo(status == ApplicationStatus.APPROVED ? StudyStatus.CLOSED : StudyStatus.RECRUITING);
    }

    private String lockedOutcome(String operation, Long applicationId, CountDownLatch start) throws Exception {
        start.await();
        try {
            transition(operation, applicationId);
            return "OK";
        } catch (com.studymatching.common.exception.BusinessException e) {
            return e.getErrorCode().name();
        }
    }

    private void transition(String operation, Long applicationId) {
        switch (operation) {
            case "approve" -> studyApplicationService.approve(studyId, applicationId, ownerUsername);
            case "reject" -> studyApplicationService.reject(studyId, applicationId, ownerUsername);
            case "cancel" -> studyApplicationService.cancel(studyId, applicationId, "applicant2");
            case "reapply" -> studyApplicationService.reapply(studyId, applicationId, "applicant2");
            default -> throw new AssertionError(operation);
        }
    }
}
