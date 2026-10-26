package com.studymatching.e2e;

import com.studymatching.studymember.entity.StudyMember;
import com.studymatching.studymember.repository.StudyMemberRepository;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.entity.StudyStatus;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.entity.ApplicationStatus;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import com.studymatching.support.PostgresTestContainerConfig;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestContainerConfig.class)
class StudyApplicationE2ETest {
    private static final String PASSWORD = "test-password123!";

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired StudyApplicationRepository applications;
    @Autowired StudyRepository studies;
    @Autowired MemberRepository members;
    @Autowired StudyMemberRepository memberships;

    private HttpClient client;
    private String ownerToken;
    private String applicantToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() throws Exception {
        // HTTP requests commit on server threads; a test transaction cannot roll them back.
        applications.deleteAll();
        studies.deleteAll();
        members.deleteAll();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1).build();
        ownerToken = registerAndLogin("owner");
        applicantToken = registerAndLogin("applicant");
        outsiderToken = registerAndLogin("outsider");
    }

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void registrationToApprovalPersistsMembershipAndClosesFullStudy() throws Exception {
        long studyId = createStudy(2);
        long applicationId = apply(studyId);
        String path = applicationPath(studyId, applicationId);

        Response pending = request("GET", applicationsPath(studyId), ownerToken, null);
        pending.expect(200);
        assertThat(pending.body().size()).isEqualTo(1);
        assertThat(pending.body().get(0).path("id").asLong()).isEqualTo(applicationId);
        assertThat(pending.body().get(0).path("status").asString()).isEqualTo("PENDING");

        Response approved = request("PATCH", path + "/approve", ownerToken, null);
        approved.expect(200);
        assertThat(approved.body().path("status").asString()).isEqualTo("APPROVED");
        assertState(studyId, applicationId, ApplicationStatus.APPROVED, 2, StudyStatus.CLOSED);

        Response study = request("GET", "/api/studies/" + studyId, applicantToken, null);
        study.expect(200);
        assertThat(study.body().path("status").asString()).isEqualTo("CLOSED");
        Response persisted = request("GET", applicationsPath(studyId), ownerToken, null);
        persisted.expect(200);
        assertThat(persisted.body().get(0).path("status").asString()).isEqualTo("APPROVED");

        request("POST", applicationsPath(studyId), outsiderToken, null)
                .expectError(409, "STUDY_NOT_RECRUITING");
        request("PATCH", path + "/cancel", applicantToken, null)
                .expectError(409, "STUDY_APPLICATION_CANNOT_CANCEL");
        assertState(studyId, applicationId, ApplicationStatus.APPROVED, 2, StudyStatus.CLOSED);
    }

    @Test
    void cancelReapplyAndRejectKeepOneApplicationAndDoNotAddMembers() throws Exception {
        long studyId = createStudy(3);
        long applicationId = apply(studyId);
        String path = applicationPath(studyId, applicationId);

        request("POST", applicationsPath(studyId), applicantToken, null)
                .expectError(409, "DUPLICATE_STUDY_APPLICATION");
        Response canceled = request("PATCH", path + "/cancel", applicantToken, null);
        canceled.expect(200);
        assertThat(canceled.body().path("status").asString()).isEqualTo("CANCELED");
        assertState(studyId, applicationId, ApplicationStatus.CANCELED, 1, StudyStatus.RECRUITING);

        Response reapplied = request("PATCH", path + "/reapply", applicantToken, null);
        reapplied.expect(200);
        assertThat(reapplied.body().path("id").asLong()).isEqualTo(applicationId);
        assertThat(reapplied.body().path("status").asString()).isEqualTo("PENDING");

        Response rejected = request("PATCH", path + "/reject", ownerToken, null);
        rejected.expect(200);
        assertThat(rejected.body().path("status").asString()).isEqualTo("REJECTED");
        request("PATCH", path + "/approve", ownerToken, null)
                .expectError(409, "STUDY_APPLICATION_ALREADY_PROCESSED");
        request("PATCH", path + "/reapply", applicantToken, null)
                .expectError(409, "STUDY_APPLICATION_CANNOT_REAPPLY");
        assertThat(applications.count()).isEqualTo(1);
        assertState(studyId, applicationId, ApplicationStatus.REJECTED, 1, StudyStatus.RECRUITING);
    }

    @Test
    void otherUsersCannotReadOrChangeAnApplication() throws Exception {
        long studyId = createStudy(2);
        long applicationId = apply(studyId);
        String path = applicationPath(studyId, applicationId);

        for (String token : new String[]{applicantToken, outsiderToken}) {
            request("GET", applicationsPath(studyId), token, null)
                    .expectError(403, "STUDY_ACCESS_DENIED");
            for (String operation : new String[]{"approve", "reject"}) {
                request("PATCH", path + "/" + operation, token, null)
                        .expectError(403, "STUDY_ACCESS_DENIED");
            }
        }
        for (String token : new String[]{ownerToken, outsiderToken}) {
            // Applicant-scoped lookup intentionally hides somebody else's application.
            for (String operation : new String[]{"cancel", "reapply"}) {
                request("PATCH", path + "/" + operation, token, null)
                        .expectError(404, "STUDY_APPLICATION_NOT_FOUND");
            }
        }
        assertState(studyId, applicationId, ApplicationStatus.PENDING, 1, StudyStatus.RECRUITING);
    }

    @Test
    void applicationIdCannotBeUsedUnderAnotherStudy() throws Exception {
        long studyId = createStudy(2);
        long applicationId = apply(studyId);
        long otherStudyId = createStudy(2);
        for (String operation : new String[]{"approve", "reject", "cancel", "reapply"}) {
            request("PATCH", applicationPath(otherStudyId, applicationId) + "/" + operation,
                    tokenFor(operation), null).expectError(404, "STUDY_APPLICATION_NOT_FOUND");
        }
        assertState(studyId, applicationId, ApplicationStatus.PENDING, 1, StudyStatus.RECRUITING);
        assertThat(memberships.countActiveMembers(otherStudyId)).isEqualTo(1);
        assertThat(studies.findById(otherStudyId).orElseThrow().getStatus())
                .isEqualTo(StudyStatus.RECRUITING);
    }

    @Test
    void missingOrInvalidAuthenticationCannotMutateData() throws Exception {
        long studyId = createStudy(2);
        long applicationId = apply(studyId);
        String path = applicationPath(studyId, applicationId);
        request("POST", "/api/auth/login", null,
                Map.of("username", "owner", "password", "incorrect-password"))
                .expectError(401, "UNAUTHORIZED");
        for (String token : new String[]{null, "invalid-token"}) {
            Response denied = request("PATCH", path + "/approve", token, null);
            denied.expectError(401, "UNAUTHORIZED");
            assertThat(denied.headers().firstValue("WWW-Authenticate").orElseThrow())
                    .startsWith("Bearer");
        }
        assertState(studyId, applicationId, ApplicationStatus.PENDING, 1, StudyStatus.RECRUITING);
    }

    @ParameterizedTest(name = "HTTP {0} commits before {1}: loser receives 409")
    @CsvSource({
            "approve,cancel", "cancel,approve", "cancel,reject", "reject,cancel"
    })
    void concurrentTransitionsReturn409AndRollBackLosingTransaction(String winner, String loser)
            throws Exception {
        long studyId = createStudy(2);
        long applicationId = apply(studyId);
        String path = applicationPath(studyId, applicationId);
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch winnerCompleted = new CountDownLatch(1);

        // Pause after real database reads; do not mock persistence or throw a synthetic conflict.
        // The request URI identifies the operation on the HTTP server's own thread.
        MethodInterceptor synchronizeReads = invocation -> {
            Object result = invocation.proceed();
            String method = invocation.getMethod().getName();
            if (!method.equals("findByIdAndStudyId")
                    && !method.equals("findByIdAndStudyIdAndApplicantUsername")) {
                return result;
            }
            var attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes servlet)) {
                return result;
            }
            String uri = servlet.getRequest().getRequestURI();
            if (!uri.equals(path + "/" + winner) && !uri.equals(path + "/" + loser)) {
                return result;
            }
            bothRead.countDown();
            if (!bothRead.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Both HTTP transactions must read the initial version");
            }
            if (uri.equals(path + "/" + loser) && !winnerCompleted.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Winning HTTP transaction must finish committing");
            }
            return result;
        };
        Advised repository = (Advised) applications;
        repository.addAdvice(0, synchronizeReads);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                try {
                    return request("PATCH", path + "/" + winner, tokenFor(winner), null);
                } finally {
                    winnerCompleted.countDown();
                }
            });
            var second = executor.submit(() ->
                    request("PATCH", path + "/" + loser, tokenFor(loser), null));
            first.get(25, TimeUnit.SECONDS).expect(200);
            second.get(25, TimeUnit.SECONDS).expectError(409, "CONCURRENT_MODIFICATION");
        } finally {
            winnerCompleted.countDown();
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                repository.removeAdvice(synchronizeReads);
            }
        }

        ApplicationStatus expected = switch (winner) {
            case "approve" -> ApplicationStatus.APPROVED;
            case "cancel" -> ApplicationStatus.CANCELED;
            case "reject" -> ApplicationStatus.REJECTED;
            default -> throw new AssertionError(winner);
        };
        // A stale approval must also roll back its attempt to close the Study.
        assertState(studyId, applicationId, expected, winner.equals("approve") ? 2 : 1,
                winner.equals("approve") ? StudyStatus.CLOSED : StudyStatus.RECRUITING);
    }

    @Test
    void concurrentApprovalAndRejectionSerializeAndReturnBusiness409() throws Exception {
        long studyId = createStudy(2);
        long applicationId = apply(studyId);
        String path = applicationPath(studyId, applicationId);
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                start.await();
                return request("PATCH", path + "/approve", ownerToken, null);
            });
            var second = executor.submit(() -> {
                start.await();
                return request("PATCH", path + "/reject", ownerToken, null);
            });
            start.countDown();
            Response approval = first.get(25, TimeUnit.SECONDS);
            Response rejection = second.get(25, TimeUnit.SECONDS);
            assertThat(java.util.List.of(approval.status(), rejection.status())).containsExactlyInAnyOrder(200, 409);
            (approval.status() == 409 ? approval : rejection)
                    .expectError(409, "STUDY_APPLICATION_ALREADY_PROCESSED");
            assertState(studyId, applicationId,
                    approval.status() == 200 ? ApplicationStatus.APPROVED : ApplicationStatus.REJECTED,
                    approval.status() == 200 ? 2 : 1,
                    approval.status() == 200 ? StudyStatus.CLOSED : StudyStatus.RECRUITING);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String registerAndLogin(String username) throws Exception {
        request("POST", "/api/auth/register", null,
                Map.of("username", username, "password", PASSWORD, "email", username + "@test.com"))
                .expect(201);
        Response login = request("POST", "/api/auth/login", null,
                Map.of("username", username, "password", PASSWORD));
        login.expect(200);
        assertThat(login.body().path("tokenType").asString()).isEqualTo("Bearer");
        String token = login.body().path("accessToken").asString();
        assertThat(token).isNotBlank();
        return token;
    }

    private long createStudy(int capacity) throws Exception {
        Response response = request("POST", "/api/studies", ownerToken,
                Map.of("title", "E2E study", "description", "Full HTTP workflow",
                        "category", "PROGRAMMING", "level", "BEGINNER", "maxMembers", capacity));
        response.expect(201);
        assertThat(response.body().path("ownerUsername").asString()).isEqualTo("owner");
        assertThat(response.body().path("status").asString()).isEqualTo("RECRUITING");
        long id = response.body().path("id").asLong();
        assertThat(id).isPositive();
        return id;
    }

    private long apply(long studyId) throws Exception {
        Response response = request("POST", applicationsPath(studyId), applicantToken, null);
        response.expect(201);
        assertThat(response.body().path("status").asString()).isEqualTo("PENDING");
        assertThat(response.body().path("applicantUsername").asString()).isEqualTo("applicant");
        long id = response.body().path("id").asLong();
        assertThat(id).isPositive();
        return id;
    }

    private void assertState(long studyId, long applicationId, ApplicationStatus status,
                             long memberCount, StudyStatus studyStatus) {
        assertThat(applications.findById(applicationId).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(memberships.countActiveMembers(studyId)).isEqualTo(memberCount);
        assertThat(studies.findById(studyId).orElseThrow().getStatus()).isEqualTo(studyStatus);
    }

    private String tokenFor(String operation) {
        return operation.equals("approve") || operation.equals("reject") ? ownerToken : applicantToken;
    }

    private String applicationsPath(long studyId) {
        return "/api/studies/" + studyId + "/applications";
    }

    private String applicationPath(long studyId, long applicationId) {
        return applicationsPath(studyId) + "/" + applicationId;
    }

    private Response request(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            builder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        }
        HttpResponse<String> response = client.send(builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.headers(), json.readTree(response.body()));
    }

    private record Response(int status, java.net.http.HttpHeaders headers, JsonNode body) {
        void expect(int expected) {
            assertThat(status).as("HTTP status; response body: %s", body).isEqualTo(expected);
            assertThat(headers.firstValue("Content-Type").orElse("")).startsWith("application/json");
        }

        void expectError(int expectedStatus, String expectedCode) {
            expect(expectedStatus);
            assertThat(body.path("status").asInt()).isEqualTo(expectedStatus);
            assertThat(body.path("code").asString()).isEqualTo(expectedCode);
            assertThat(body.path("message").asString()).isNotBlank();
            assertThat(body.path("timestamp").asString()).isNotBlank();
        }
    }
}
