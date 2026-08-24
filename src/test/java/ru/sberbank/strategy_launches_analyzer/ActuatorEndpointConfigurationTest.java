package ru.sberbank.strategy_launches_analyzer;

import ru.sberbank.strategy_launches_analyzer.dto.agent.TokenUsage;
import ru.sberbank.strategy_launches_analyzer.service.agent.AgentMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "strategy-launches-analyzer.agent.gigachat.auth-mode=user-password",
                "strategy-launches-analyzer.agent.gigachat.model=test-model",
                "strategy-launches-analyzer.agent.gigachat.user-password.api-url=https://api.example/v1",
                "strategy-launches-analyzer.agent.gigachat.user-password.auth-api-url=https://auth.example/v1",
                "strategy-launches-analyzer.agent.gigachat.user-password.username=test-user",
                "strategy-launches-analyzer.agent.gigachat.user-password.password=test-password",
                "strategy-launches-analyzer.agent.gigachat.user-password.scope=GIGACHAT_API_PERS"
        }
)
class ActuatorEndpointConfigurationTest {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private AgentMetrics agentMetrics;

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
        assertStatus("/v3/api-docs", 404);
    }

    @Test
    void exposesAgentAndContractInfoMetrics() throws Exception {
        agentMetrics.recordAnalysis(
                "LGD_DIGITAL",
                AgentMetrics.AnalysisOutcome.COMPLETED,
                1_000_000L,
                List.of(),
                new TokenUsage(2, 1, 3, 0L, 0L, "test-model")
        );
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/actuator/prometheus"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("strategy_launches_agent_info"));
        assertTrue(response.body().contains("strategy_launches_contract_info"));
        assertTrue(response.body().contains("strategy_analysis_llm_model_info"));
        assertTrue(response.body().contains("strategy_analysis_llm_input_tokens_total"));
        assertTrue(response.body().contains("strategy_analysis_llm_output_tokens_total"));
        assertTrue(response.body().contains("strategy_analysis_llm_requests_total"));
        assertTrue(response.body().contains("prompt_hash="));
        assertTrue(response.body().contains("contract_version="));
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
