package com.studymatching.e2e;

import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.entity.StudyStatus;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.entity.ApplicationStatus;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import com.studymatching.studymember.entity.StudyMemberStatus;
import com.studymatching.studymember.repository.StudyMemberRepository;
import com.studymatching.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestContainerConfig.class)
class StudyMemberE2ETest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired StudyRepository studies;
    @Autowired MemberRepository members;
    @Autowired StudyApplicationRepository applications;
    @Autowired StudyMemberRepository memberships;
    private HttpClient client;
    private User owner, applicant, outsider;
    private long studyId, applicationId, otherApplicationId;

    @BeforeEach
    void setUp() throws Exception {
        applications.deleteAll();
        studies.deleteAll();
        members.deleteAll();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        owner = register("owner");
        applicant = register("applicant");
        outsider = register("outsider");
        studyId = call("POST", "/api/studies", owner, studyBody(2)).expect(201).body().path("id").asLong();
        applicationId = call("POST", path("/applications"), applicant, null).expect(201).body().path("id").asLong();
        otherApplicationId = call("POST", path("/applications"), outsider, null).expect(201).body().path("id").asLong();
        call("PATCH", application(applicationId, "approve"), owner, null).expect(200);
        assertCapacity(2, StudyStatus.CLOSED);
    }

    @AfterEach
    void close() {
        if (client != null) client.close();
    }

    @Test
    void leaveReopensStudyAndReapprovalReactivatesSameMembership() throws Exception {
        long membershipId = memberships.findByStudyIdAndMemberId(studyId, applicant.id()).orElseThrow().getId();
        call("DELETE", path("/members/me"), applicant, null).expect(204);
        assertMembership(applicant, StudyMemberStatus.LEFT);
        assertCapacity(1, StudyStatus.RECRUITING);
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(call("GET", path("/members"), owner, null).expect(200).body().size()).isEqualTo(1);
        call("GET", path("/members"), applicant, null).error(403, "STUDY_ACCESS_DENIED");
        call("PATCH", application(applicationId, "reapply"), applicant, null).expect(200);
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertCapacity(1, StudyStatus.RECRUITING);
        call("PATCH", application(applicationId, "approve"), owner, null).expect(200);
        assertMembership(applicant, StudyMemberStatus.ACTIVE);
        assertCapacity(2, StudyStatus.CLOSED);
        assertThat(memberships.findByStudyIdAndMemberId(studyId, applicant.id()).orElseThrow().getId())
                .isEqualTo(membershipId);
    }

    @Test
    void removedMemberCannotReapplyOrCreateAnotherApplication() throws Exception {
        call("DELETE", path("/members/" + applicant.id()), owner, null).expect(204);
        assertMembership(applicant, StudyMemberStatus.REMOVED);
        assertCapacity(1, StudyStatus.RECRUITING);
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        call("PATCH", application(applicationId, "reapply"), applicant, null).error(403, "STUDY_MEMBER_REMOVED");
        call("POST", path("/applications"), applicant, null).error(403, "STUDY_MEMBER_REMOVED");
        call("GET", path("/members"), applicant, null).error(403, "STUDY_ACCESS_DENIED");
        assertThat(memberships.count()).isEqualTo(2);
    }

    @Test
    void transferChangesAuthorityAndFormerOwnerCanLeaveAndApply() throws Exception {
        Response response = call("PATCH", path("/owner"), owner, Map.of("memberId", applicant.id())).expect(200);
        assertThat(response.body().path("role").asString()).isEqualTo("OWNER");
        assertThat(studies.findById(studyId).orElseThrow().getOwner().getId()).isEqualTo(applicant.id());
        JsonNode list = call("GET", path("/members"), applicant, null).expect(200).body();
        assertThat(list.size()).isEqualTo(2);
        assertThat(List.of(list.get(0).path("role").asString(), list.get(1).path("role").asString()))
                .containsExactlyInAnyOrder("OWNER", "MEMBER");
        assertCapacity(2, StudyStatus.CLOSED);
        for (String operation : List.of("approve", "reject")) {
            call("PATCH", application(otherApplicationId, operation), owner, null).error(403, "STUDY_ACCESS_DENIED");
        }
        call("PUT", path(""), owner, studyBody(3)).error(403, "STUDY_ACCESS_DENIED");
        call("DELETE", path(""), owner, null).error(403, "STUDY_ACCESS_DENIED");
        call("DELETE", path("/members/me"), applicant, null).error(409, "STUDY_OWNER_CANNOT_LEAVE");
        call("DELETE", path("/members/me"), owner, null).expect(204);
        assertCapacity(1, StudyStatus.RECRUITING);
        long formerOwnerApplication = call("POST", path("/applications"), owner, null).expect(201).body().path("id").asLong();
        call("PATCH", application(formerOwnerApplication, "approve"), applicant, null).expect(200);
        assertMembership(owner, StudyMemberStatus.ACTIVE);
        assertCapacity(2, StudyStatus.CLOSED);
    }

    @Test
    void ownershipAndMembershipBoundariesRejectInvalidOperations() throws Exception {
        call("DELETE", path("/members/me"), owner, null).error(409, "STUDY_OWNER_CANNOT_LEAVE");
        call("DELETE", path("/members/" + owner.id()), owner, null).error(409, "STUDY_OWNER_CANNOT_LEAVE");
        call("PATCH", path("/owner"), owner, Map.of("memberId", owner.id())).error(409, "STUDY_OWNER_TRANSFER_TO_SELF");
        call("PATCH", path("/owner"), owner, Map.of("memberId", outsider.id())).error(404, "STUDY_MEMBER_NOT_FOUND");
        call("PATCH", path("/owner"), owner, Map.of("memberId", -1)).error(400, "VALIDATION_FAILED");
        call("PATCH", path("/owner"), owner, Map.of()).error(400, "VALIDATION_FAILED");
        for (User user : List.of(applicant, outsider)) {
            call("PATCH", path("/owner"), user, Map.of("memberId", applicant.id())).error(403, "STUDY_ACCESS_DENIED");
            call("DELETE", path("/members/" + applicant.id()), user, null).error(403, "STUDY_ACCESS_DENIED");
        }
        call("GET", path("/members"), outsider, null).error(403, "STUDY_ACCESS_DENIED");
        call("GET", path("/members"), null, null).error(401, "UNAUTHORIZED");
        call("DELETE", path("/members/me"), outsider, null).error(404, "STUDY_MEMBER_NOT_FOUND");
        assertCapacity(2, StudyStatus.CLOSED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void inactiveMemberCannotReceiveOwnershipOrBeRemovedAgain(boolean removed) throws Exception {
        call("DELETE", path(removed ? "/members/" + applicant.id() : "/members/me"),
                removed ? owner : applicant, null).expect(204);
        call("PATCH", path("/owner"), owner, Map.of("memberId", applicant.id())).error(409, "STUDY_MEMBER_NOT_ACTIVE");
        call("DELETE", path("/members/" + applicant.id()), owner, null).error(409, "STUDY_MEMBER_NOT_ACTIVE");
        call("DELETE", path("/members/me"), applicant, null).error(409, "STUDY_MEMBER_NOT_ACTIVE");
        assertCapacity(1, StudyStatus.RECRUITING);
    }

    @Test
    void membershipIdsAreScopedToStudyAndDeletionCascades() throws Exception {
        long anotherStudy = call("POST", "/api/studies", outsider, studyBody(2)).expect(201).body().path("id").asLong();
        call("DELETE", "/api/studies/" + anotherStudy + "/members/" + applicant.id(), outsider, null)
                .error(404, "STUDY_MEMBER_NOT_FOUND");
        call("PATCH", "/api/studies/" + anotherStudy + "/owner", outsider, Map.of("memberId", applicant.id()))
                .error(404, "STUDY_MEMBER_NOT_FOUND");
        assertMembership(applicant, StudyMemberStatus.ACTIVE);
        call("DELETE", path(""), owner, null).expect(204);
        assertThat(memberships.countActiveMembers(studyId)).isZero();
        assertThat(applications.findById(applicationId)).isEmpty();
        assertThat(memberships.countActiveMembers(anotherStudy)).isEqualTo(1);
    }

    @Test
    void capacityChangesUseActiveMembersInsteadOfApprovedHistory() throws Exception {
        call("DELETE", path("/members/me"), applicant, null).expect(204);
        call("PUT", path(""), owner, studyBody(1)).expect(200);
        assertCapacity(1, StudyStatus.CLOSED);
        call("PUT", path(""), owner, studyBody(2)).expect(200);
        call("PATCH", application(otherApplicationId, "approve"), owner, null).expect(200);
        assertCapacity(2, StudyStatus.CLOSED);
        assertThat(applications.countByStudyIdAndStatus(studyId, ApplicationStatus.APPROVED)).isEqualTo(2);
        call("PUT", path(""), owner, studyBody(1)).error(409, "STUDY_CAPACITY_BELOW_CURRENT_MEMBERS");
    }

    @Test
    void competingApprovalsAfterLeaveCannotExceedCapacity() throws Exception {
        call("DELETE", path("/members/me"), applicant, null).expect(204);
        call("PATCH", application(applicationId, "reapply"), applicant, null).expect(200);
        List<Response> outcomes = race(
                () -> call("PATCH", application(applicationId, "approve"), owner, null),
                () -> call("PATCH", application(otherApplicationId, "approve"), owner, null));
        assertThat(outcomes.stream().map(Response::status).toList()).containsExactlyInAnyOrder(200, 409);
        outcomes.stream().filter(r -> r.status() == 409).findFirst().orElseThrow().error(409, "STUDY_CAPACITY_EXCEEDED");
        assertCapacity(2, StudyStatus.CLOSED);
    }

    @Test
    void transferRacingWithLeaveKeepsOwnerActive() throws Exception {
        List<Response> outcomes = race(
                () -> call("PATCH", path("/owner"), owner, Map.of("memberId", applicant.id())),
                () -> call("DELETE", path("/members/me"), applicant, null));
        if (outcomes.get(0).status() == 200) {
            outcomes.get(1).error(409, "STUDY_OWNER_CANNOT_LEAVE");
            assertCapacity(2, StudyStatus.CLOSED);
        } else {
            outcomes.get(0).error(409, "STUDY_MEMBER_NOT_ACTIVE");
            outcomes.get(1).expect(204);
            assertCapacity(1, StudyStatus.RECRUITING);
        }
        long ownerId = studies.findById(studyId).orElseThrow().getOwner().getId();
        assertThat(memberships.findByStudyIdAndMemberId(studyId, ownerId).orElseThrow().getStatus())
                .isEqualTo(StudyMemberStatus.ACTIVE);
    }

    @Test
    void competingTransfersHaveOnlyOneSuccessfulOldOwnerRequest() throws Exception {
        call("PUT", path(""), owner, studyBody(3)).expect(200);
        call("PATCH", application(otherApplicationId, "approve"), owner, null).expect(200);
        List<Response> outcomes = race(
                () -> call("PATCH", path("/owner"), owner, Map.of("memberId", applicant.id())),
                () -> call("PATCH", path("/owner"), owner, Map.of("memberId", outsider.id())));
        assertThat(outcomes.stream().map(Response::status).toList()).containsExactlyInAnyOrder(200, 403);
        outcomes.stream().filter(r -> r.status() == 403).findFirst().orElseThrow().error(403, "STUDY_ACCESS_DENIED");
        assertThat(studies.findById(studyId).orElseThrow().getOwner().getId()).isIn(applicant.id(), outsider.id());
        assertCapacity(3, StudyStatus.CLOSED);
    }

    @Test
    void leaveRacingWithRemovalHasOneStateChange() throws Exception {
        List<Response> outcomes = race(
                () -> call("DELETE", path("/members/me"), applicant, null),
                () -> call("DELETE", path("/members/" + applicant.id()), owner, null));
        assertThat(outcomes.stream().map(Response::status).toList()).containsExactlyInAnyOrder(204, 409);
        outcomes.stream().filter(r -> r.status() == 409).findFirst().orElseThrow().error(409, "STUDY_MEMBER_NOT_ACTIVE");
        assertCapacity(1, StudyStatus.RECRUITING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void previousOwnerWaitingOnTransferCannotProcessApplications(String operation) throws Exception {
        call("PUT", path(""), owner, studyBody(3)).expect(200);
        CountDownLatch transferLocked = new CountDownLatch(1);
        CountDownLatch oldOwnerAttempted = new CountDownLatch(1);
        org.aopalliance.intercept.MethodInterceptor orderLocks = invocation -> {
            if (!invocation.getMethod().getName().equals("findByIdForUpdate")) return invocation.proceed();
            var attributes = (org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
            if (attributes.getRequest().getRequestURI().endsWith("/owner")) {
                Object result = invocation.proceed();
                transferLocked.countDown();
                if (!oldOwnerAttempted.await(10, TimeUnit.SECONDS)) throw new AssertionError("Old owner must attempt lock");
                return result;
            }
            if (!transferLocked.await(10, TimeUnit.SECONDS)) throw new AssertionError("Transfer must hold lock first");
            oldOwnerAttempted.countDown();
            return invocation.proceed();
        };
        var repository = (org.springframework.aop.framework.Advised) studies;
        repository.addAdvice(0, orderLocks);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var transfer = executor.submit(() -> call("PATCH", path("/owner"), owner, Map.of("memberId", applicant.id())));
            var processing = executor.submit(() -> call("PATCH", application(otherApplicationId, operation), owner, null));
            transfer.get(25, TimeUnit.SECONDS).expect(200);
            processing.get(25, TimeUnit.SECONDS).error(403, "STUDY_ACCESS_DENIED");
        } finally {
            transferLocked.countDown();
            oldOwnerAttempted.countDown();
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                repository.removeAdvice(orderLocks);
            }
        }
        assertThat(applications.findById(otherApplicationId).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(studies.findById(studyId).orElseThrow().getOwner().getId()).isEqualTo(applicant.id());
        assertCapacity(2, StudyStatus.RECRUITING);
    }

    @Test
    void studyAndProfileShareCanonicalScheduleAndEnums() throws Exception {
        var body = new java.util.HashMap<String,Object>(studyBody(3));
        body.put("category", " ai "); body.put("level", " beginner ");
        body.put("days", List.of("wed", " monday ")); body.put("startTime", "19:00"); body.put("endTime", "21:00");
        var response = call("PUT", path(""), owner, body).expect(200).body();
        assertThat(response.path("category").asString()).isEqualTo("AI");
        assertThat(response.path("days").get(0).asString()).isEqualTo("MONDAY");
        assertThat(response.path("days").get(1).asString()).isEqualTo("WEDNESDAY");
        assertThat(response.path("startTime").asString()).isEqualTo("19:00");
        var persisted = call("GET", path(""), owner, null).expect(200).body();
        assertThat(persisted.path("days")).isEqualTo(response.path("days"));
        var profile = new java.util.HashMap<>(body); profile.put("department", "Computer Science");
        profile.remove("title"); profile.remove("maxMembers");
        var savedProfile = call("PUT", "/api/profiles/me", owner, profile).expect(200).body();
        assertThat(savedProfile.path("days")).isEqualTo(response.path("days"));
        assertThat(savedProfile.path("endTime").asString()).isEqualTo("21:00");
        assertThat(call("GET", "/api/profiles/me", owner, null).expect(200).body().path("days"))
                .isEqualTo(response.path("days"));
        var search = call("GET", "/api/studies?category=ai&level=beginner", owner, null).expect(200).body();
        assertThat(search.path("totalElements").asLong()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings={"duplicate", "unknownDay", "stringDays", "partial", "reverse", "same", "seconds", "24hour", "category", "level"})
    void invalidSchedulesCannotChangeStudy(String invalid) throws Exception {
        var body = new java.util.HashMap<String,Object>(studyBody(3));
        body.put("days", List.of("MONDAY")); body.put("startTime", "19:00"); body.put("endTime", "21:00");
        switch (invalid) {
            case "duplicate" -> body.put("days", List.of("MON", "MONDAY"));
            case "unknownDay" -> body.put("days", List.of("SOMEDAY"));
            case "stringDays" -> body.put("days", "MONDAY,WEDNESDAY");
            case "partial" -> body.remove("endTime");
            case "reverse" -> body.put("startTime", "22:00");
            case "same" -> body.put("startTime", "21:00");
            case "seconds" -> body.put("startTime", "19:00:30");
            case "24hour" -> body.put("startTime", "24:00");
            case "category" -> body.put("category", "UNKNOWN");
            case "level" -> body.put("level", "MASTER");
        }
        call("PUT", path(""), owner, body).expect(400);
        assertThat(studies.findById(studyId).orElseThrow().getCategory()).isEqualTo(com.studymatching.study.entity.StudyCategory.PROGRAMMING);
        assertThat(studies.findById(studyId).orElseThrow().getMaxMembers()).isEqualTo(2);
    }

    private List<Response> race(Callable<Response> first, Callable<Response> second) throws Exception {
        CountDownLatch bothLockAttempts = new CountDownLatch(2);
        org.aopalliance.intercept.MethodInterceptor synchronizeLockAttempts = invocation -> {
            if (invocation.getMethod().getName().equals("findByIdForUpdate")
                    && invocation.getArguments()[0].equals(studyId)) {
                bothLockAttempts.countDown();
                if (!bothLockAttempts.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Both HTTP requests must reach the real Study lock");
                }
            }
            return invocation.proceed();
        };
        var repository = (org.springframework.aop.framework.Advised) studies;
        repository.addAdvice(0, synchronizeLockAttempts);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            var b = executor.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                repository.removeAdvice(synchronizeLockAttempts);
            }
        }
    }

    private void assertMembership(User user, StudyMemberStatus status) {
        assertThat(memberships.findByStudyIdAndMemberId(studyId, user.id()).orElseThrow().getStatus()).isEqualTo(status);
    }

    private void assertCapacity(long count, StudyStatus status) {
        assertThat(memberships.countActiveMembers(studyId)).isEqualTo(count);
        assertThat(studies.findById(studyId).orElseThrow().getStatus()).isEqualTo(status);
    }

    private User register(String username) throws Exception {
        long id = call("POST", "/api/auth/register", null,
                Map.of("username", username, "password", "password123!", "email", username + "@test.com"))
                .expect(201).body().path("id").asLong();
        String token = call("POST", "/api/auth/login", null, Map.of("username", username, "password", "password123!"))
                .expect(200).body().path("accessToken").asString();
        return new User(id, token);
    }

    private Map<String, Object> studyBody(int capacity) {
        return Map.of("title", "Lifecycle study", "category", "PROGRAMMING", "level", "BEGINNER", "maxMembers", capacity);
    }

    private String path(String suffix) { return "/api/studies/" + studyId + suffix; }
    private String application(long id, String action) { return path("/applications/" + id + "/" + action); }

    private Response call(String method, String path, User user, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
        if (user != null) builder.header("Authorization", "Bearer " + user.token());
        var publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        }
        var response = client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body().isBlank() ? json.nullNode() : json.readTree(response.body()));
    }

    private record User(long id, String token) { }
    private record Response(int status, JsonNode body) {
        Response expect(int expected) {
            assertThat(status).as("HTTP response: %s", body).isEqualTo(expected);
            return this;
        }
        void error(int status, String code) {
            expect(status);
            assertThat(body.path("status").asInt()).isEqualTo(status);
            assertThat(body.path("code").asString()).isEqualTo(code);
            assertThat(body.path("message").asString()).isNotBlank();
            assertThat(body.path("timestamp").asString()).isNotBlank();
        }
    }
}
