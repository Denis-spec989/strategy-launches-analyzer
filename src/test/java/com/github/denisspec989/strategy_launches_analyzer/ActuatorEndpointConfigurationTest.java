package com.github.denisspec989.strategy_launches_analyzer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "OPENAI_API_KEY=dummy-test-key"
)
@ActiveProfiles("openai")
class ActuatorEndpointConfigurationTest {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void exposesHealthProbeAndPrometheusEndpoints() throws Exception {
        assertOk("/actuator/health");
        assertOk("/actuator/health/liveness");
        assertOk("/actuator/health/readiness");
        assertOk("/actuator/prometheus");
    }

    @Test
    void doesNotExposeSensitiveActuatorEndpoints() throws Exception {
        assertStatus("/actuator/env", 404);
    }

    private void assertOk(String path) throws Exception {
        assertStatus(path, 200);
    }

    private void assertStatus(String path, int expectedStatus) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertEquals(expectedStatus, response.statusCode(), path);
    }
}
