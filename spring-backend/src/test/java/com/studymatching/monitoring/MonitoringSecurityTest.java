package com.studymatching.monitoring;

import com.studymatching.auth.dto.LoginRequest;
import com.studymatching.auth.dto.RegisterRequest;
import com.studymatching.auth.service.AuthService;
import com.studymatching.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "monitoring"})
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "monitoring.password=monitoring-test-password-at-least-32-characters")
@Import(PostgresTestContainerConfig.class)
class MonitoringSecurityTest {
    static final String METRICS_PASSWORD = "monitoring-test-password-at-least-32-characters";
    @LocalServerPort int port;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    HttpClient client;

    @BeforeEach void open() { client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); }
    @AfterEach void close() { client.close(); }

    @Test
    void anonymousScrapeIsRejectedWithBasicChallenge() throws Exception {
        var response = get("/actuator/prometheus", null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Basic realm=\"metrics\"");
        assertThat(json.readTree(response.body()).path("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(response.body()).doesNotContain("jvm_memory", METRICS_PASSWORD);
    }

    @Test
    void wrongScrapePasswordIsRejected() throws Exception {
        assertThat(get("/actuator/prometheus", basic("prometheus", "wrong")).statusCode()).isEqualTo(401);
    }

    @Test
    void dedicatedCredentialsExportJvmPoolAndHttpHistogram() throws Exception {
        get("/actuator/health", null);
        var response = get("/actuator/prometheus", basic("prometheus", METRICS_PASSWORD));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes", "hikaricp_connections_max",
                "http_server_requests_seconds_bucket", "application=\"study-matching-api\"")
                .doesNotContain(METRICS_PASSWORD);
    }

    @Test
    void businessJwtCannotReadMetricsAndBusinessAuthenticationStillWorks() throws Exception {
        String user = "monitor-" + UUID.randomUUID();
        auth.register(new RegisterRequest(user, "password123!", user + "@test.com"));
        var pair = auth.login(new LoginRequest(user, "password123!"));
        assertThat(get("/api/members/me", "Bearer " + pair.accessToken()).statusCode()).isEqualTo(200);
        assertThat(get("/actuator/prometheus", "Bearer " + pair.accessToken()).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/prometheus", basic(user, "password123!")).statusCode()).isEqualTo(401);
    }

    @Test
    void monitoringCredentialsCannotCallBusinessApi() throws Exception {
        assertThat(get("/api/members/me", basic("prometheus", METRICS_PASSWORD)).statusCode()).isEqualTo(401);
    }

    @Test
    void publicHealthDoesNotExposeDetails() throws Exception {
        var response = get("/actuator/health", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).has("details")).isFalse();
        assertThat(json.readTree(response.body()).has("components")).isFalse();
        assertThat(json.readTree(response.body()).path("status").asString()).isEqualTo("UP");
    }

    @Test
    void metricsCredentialsDoNotExposeOtherActuatorEndpoints() throws Exception {
        for (String endpoint : new String[]{"env", "configprops", "heapdump", "metrics"}) {
            assertThat(get("/actuator/" + endpoint, basic("prometheus", METRICS_PASSWORD)).statusCode()).isEqualTo(401);
        }
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET();
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
