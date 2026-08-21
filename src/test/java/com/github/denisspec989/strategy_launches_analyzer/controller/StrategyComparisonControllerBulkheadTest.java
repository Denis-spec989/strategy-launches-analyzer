package com.github.denisspec989.strategy_launches_analyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "strategy-launches-analyzer.agent.max-concurrent-calls=0",
        "strategy-launches-analyzer.agent.gigachat.auth-mode=user-password",
        "strategy-launches-analyzer.agent.gigachat.model=test-model",
        "strategy-launches-analyzer.agent.gigachat.user-password.api-url=https://api.example/v1",
        "strategy-launches-analyzer.agent.gigachat.user-password.auth-api-url=https://auth.example/v1",
        "strategy-launches-analyzer.agent.gigachat.user-password.username=test-user",
        "strategy-launches-analyzer.agent.gigachat.user-password.password=test-password",
        "strategy-launches-analyzer.agent.gigachat.user-password.scope=GIGACHAT_API_PERS"
})
@AutoConfigureMockMvc
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Autowired)
class StrategyComparisonControllerBulkheadTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Test
    void returnsDeterministicFallbackWhenBulkheadIsFull() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalDiffs").value(3))
                .andExpect(jsonPath("$.diffs.length()").value(3))
                .andExpect(jsonPath("$.contractValidation").isArray())
                .andExpect(jsonPath("$.agentAnalysis.status").value("FAILED"))
                .andExpect(jsonPath("$.agentAnalysis.overallSeverity").value("WARNING"))
                .andExpect(jsonPath("$.agentAnalysis.errorMessage").value("LLM-анализ недоступен."));
    }

    private String requestBody() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("strategy", StrategyName.LGD_DIGITAL.name());
        body.set("mainLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/main.json"
        ));
        body.set("shadowLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/shadow.json"
        ));
        return objectMapper.writeValueAsString(body);
    }

    @TestConfiguration
    static class UnexpectedAgentConfiguration {
        @Bean
        @Primary
        AgentAnalyzer unexpectedAgentAnalyzer() {
            return input -> {
                throw new AssertionError("Bulkhead must reject the call before agent execution.");
            };
        }
    }
}
