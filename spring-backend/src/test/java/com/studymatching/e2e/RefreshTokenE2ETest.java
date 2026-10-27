package com.studymatching.e2e;

import com.studymatching.auth.security.JwtTokenService;
import com.studymatching.auth.repository.RefreshTokenRepository;
import com.studymatching.auth.service.RefreshTokenService;
import com.studymatching.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestContainerConfig.class)
class RefreshTokenE2ETest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired RefreshTokenService refreshService;
    @MockitoSpyBean JwtTokenService jwt;
    @MockitoSpyBean RefreshTokenRepository tokens;
    HttpClient client;
    String username;
    static final String PASSWORD = "password123!";

    @BeforeEach
    void setUp() throws Exception {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        username = "rt-" + UUID.randomUUID();
        assertThat(post("register", Map.of("username", username, "password", PASSWORD,
                "email", username + "@test.com")).statusCode()).isEqualTo(201);
    }

    @AfterEach
    void closeClient() { client.close(); }

    @Test
    void loginIssuesUniquePairAndStoresOnlyHashWithNoCacheHeaders() throws Exception {
        var response = post("login", Map.of("username", username, "password", PASSWORD));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Pragma")).hasValue("no-cache");
        JsonNode pair = body(response);
        String raw = refresh(pair);
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        assertThat(pair.path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(pair.path("expiresIn").asLong()).isEqualTo(jwt.getExpirationSeconds());
        assertThat(pair.path("refreshExpiresIn").asLong()).isEqualTo(1209600);
        assertThat(decoder.decode(access(pair)).getSubject()).isEqualTo(username);
        assertThat(db.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, hash(raw))).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, raw)).isZero();
        JsonNode second = login();
        assertThat(refresh(second)).isNotEqualTo(raw);
        assertThat(access(second)).isNotEqualTo(access(pair));
        assertThat(sessionId(raw)).isNotEqualTo(sessionId(refresh(second)));
    }

    @Test
    void rotationReplacesBothTokensAndKeepsAbsoluteExpiry() throws Exception {
        JsonNode first = login();
        String raw = refresh(first);
        UUID id = sessionId(raw);
        Instant expiry = expiry(id);
        var response = refreshCall(raw);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        JsonNode next = body(response);
        assertThat(refresh(next)).isNotEqualTo(raw);
        assertThat(access(next)).isNotEqualTo(access(first));
        assertThat(sessionId(refresh(next))).isEqualTo(id);
        assertThat(expiry(id)).isEqualTo(expiry);
        assertThat(db.queryForObject("select consumed_at is not null from refresh_tokens where token_hash = ?", Boolean.class, hash(raw))).isTrue();
        assertThat(protectedCall(access(next)).statusCode()).isEqualTo(200);
        assertThat(refreshCall(refresh(next)).statusCode()).isEqualTo(200);
    }

    @Test
    void replayRevokesLatestDescendantButNotAnotherLogin() throws Exception {
        JsonNode first = login();
        JsonNode otherLogin = login();
        JsonNode second = body(refreshCall(refresh(first)));
        JsonNode third = body(refreshCall(refresh(second)));
        assertInvalid(refreshCall(refresh(first)));
        // A fresh HTTP transaction must observe the revocation despite the previous 401.
        assertInvalid(refreshCall(refresh(third)));
        assertThat(refreshCall(refresh(otherLogin)).statusCode()).isEqualTo(200);
    }

    @Test
    void logoutWithConsumedTokenRevokesFamilyAndIsIdempotent() throws Exception {
        JsonNode first = login();
        JsonNode second = body(refreshCall(refresh(first)));
        assertThat(logout(refresh(first)).statusCode()).isEqualTo(204);
        assertThat(logout(refresh(first)).statusCode()).isEqualTo(204);
        assertThat(logout("A".repeat(43)).statusCode()).isEqualTo(204);
        assertInvalid(refreshCall(refresh(second)));
        // Stateless access JWTs deliberately remain valid until their own expiry.
        assertThat(protectedCall(access(second)).statusCode()).isEqualTo(200);
    }

    @Test
    void logoutDoesNotRevokeAnotherLogin() throws Exception {
        JsonNode first = login();
        JsonNode other = login();
        assertThat(logout(refresh(first)).statusCode()).isEqualTo(204);
        assertInvalid(refreshCall(refresh(first)));
        assertThat(refreshCall(refresh(other)).statusCode()).isEqualTo(200);
    }

    @Test
    void expiredSessionCannotRefreshAndCleanupCascadesOnlyExpiredFamilies() throws Exception {
        JsonNode expired = login();
        JsonNode active = login();
        UUID id = sessionId(refresh(expired));
        db.update("update refresh_sessions set created_at = now() - interval '2 days', expires_at = now() - interval '1 day' where id = ?", id);
        assertInvalid(refreshCall(refresh(expired)));
        assertThat(refreshService.deleteExpiredSessions()).isGreaterThanOrEqualTo(1);
        assertThat(db.queryForObject("select count(*) from refresh_tokens where session_id = ?", Integer.class, id)).isZero();
        assertThat(refreshCall(refresh(active)).statusCode()).isEqualTo(200);
    }

    @Test
    void expiredAccessTokenCanBeReplacedWithoutSendingItToRefreshEndpoint() throws Exception {
        JsonNode pair = login();
        Instant now = Instant.now();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("study-matching-api").subject(username)
                        .issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(120)).build())).getTokenValue();
        assertThat(protectedCall(expired).statusCode()).isEqualTo(401);
        var refreshed = refreshCall(refresh(pair));
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(protectedCall(access(body(refreshed))).statusCode()).isEqualTo(200);
    }

    @Test
    void refreshTokenIsNotAnAccessTokenAndUnknownTokenReturns401() throws Exception {
        JsonNode pair = login();
        assertThat(protectedCall(refresh(pair)).statusCode()).isEqualTo(401);
        assertThat(refreshCall(access(pair)).statusCode()).isEqualTo(400);
        assertInvalid(refreshCall("A".repeat(43)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"refreshToken\":null}", "{\"refreshToken\":\"\"}",
            "{\"refreshToken\":\" \"}", "{\"refreshToken\":\"short\"}", "{"})
    void malformedRefreshAndLogoutRequestsReturn400(String requestBody) throws Exception {
        for (String endpoint : new String[]{"refresh", "logout"}) {
            assertThat(send(endpoint, requestBody).statusCode()).isEqualTo(400);
        }
    }

    @Test
    void simultaneousRefreshAllowsOnlyOneRotationAndThenRevokesTheFamily() throws Exception {
        String token = refresh(login());
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<HttpResponse<String>> call = () -> { start.await(); return refreshCall(token); };
            var first = executor.submit(call);
            var second = executor.submit(call);
            start.countDown();
            var a = first.get(15, TimeUnit.SECONDS);
            var b = second.get(15, TimeUnit.SECONDS);
            assertThat(new int[]{a.statusCode(), b.statusCode()}).containsExactlyInAnyOrder(200, 401);
            JsonNode winner = body(a.statusCode() == 200 ? a : b);
            assertInvalid(refreshCall(refresh(winner)));
            assertThat(db.queryForObject("select count(*) from refresh_tokens where session_id = ?", Integer.class, sessionId(token))).isEqualTo(2);
        }
    }

    @Test
    void logoutRacingRotationLeavesNoUsableRefreshToken() throws Exception {
        String token = refresh(login());
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var rotating = executor.submit(() -> { start.await(); return refreshCall(token); });
            var loggingOut = executor.submit(() -> { start.await(); return logout(token); });
            start.countDown();
            var response = rotating.get(15, TimeUnit.SECONDS);
            assertThat(loggingOut.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(204);
            assertThat(response.statusCode()).isIn(200, 401);
            if (response.statusCode() == 200) assertInvalid(refreshCall(refresh(body(response))));
            assertInvalid(refreshCall(token));
        }
    }

    @Test
    void signingFailureRollsBackConsumptionAndTokenInsertion() throws Exception {
        String token = refresh(login());
        doThrow(new IllegalStateException("simulated signer failure")).doCallRealMethod()
                .when(jwt).createAccessToken(username);
        assertThat(refreshCall(token).statusCode()).isEqualTo(500);
        assertThat(db.queryForObject("select consumed_at is null from refresh_tokens where token_hash = ?", Boolean.class, hash(token))).isTrue();
        assertThat(db.queryForObject("select count(*) from refresh_tokens where session_id = ?", Integer.class, sessionId(token))).isEqualTo(1);
        assertThat(refreshCall(token).statusCode()).isEqualTo(200);
    }

    @Test
    void refreshStorageFailureReturns500RatherThan401() throws Exception {
        String token = refresh(login());
        doThrow(new DataAccessResourceFailureException("simulated storage failure"))
                .when(tokens).findSessionId(hash(token));
        var response = refreshCall(token);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(body(response).path("code").asString()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.body()).doesNotContain("simulated storage failure", token);
    }

    private JsonNode login() throws Exception {
        var response = post("login", Map.of("username", username, "password", PASSWORD));
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response);
    }
    private HttpResponse<String> refreshCall(String token) throws Exception { return post("refresh", Map.of("refreshToken", token)); }
    private HttpResponse<String> logout(String token) throws Exception { return post("logout", Map.of("refreshToken", token)); }
    private HttpResponse<String> post(String endpoint, Object body) throws Exception { return send(endpoint, json.writeValueAsString(body)); }
    private HttpResponse<String> send(String endpoint, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/" + endpoint))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> protectedCall(String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/members/me"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }
    private String refresh(JsonNode pair) { return pair.path("refreshToken").asString(); }
    private String access(JsonNode pair) { return pair.path("accessToken").asString(); }
    private void assertInvalid(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(body(response).path("code").asString()).isEqualTo("INVALID_REFRESH_TOKEN");
    }
    private UUID sessionId(String raw) throws Exception {
        return db.queryForObject("select session_id from refresh_tokens where token_hash = ?", UUID.class, hash(raw));
    }
    private Instant expiry(UUID id) {
        return db.queryForObject("select expires_at from refresh_sessions where id = ?", java.sql.Timestamp.class, id).toInstant();
    }
    private String hash(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII)));
    }
}
