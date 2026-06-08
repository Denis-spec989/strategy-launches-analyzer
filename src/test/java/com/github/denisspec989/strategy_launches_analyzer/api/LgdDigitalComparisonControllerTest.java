package com.github.denisspec989.strategy_launches_analyzer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.agent.AgentAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "strategy-launches-analyzer.agent.provider=fallback")
@AutoConfigureMockMvc
class LgdDigitalComparisonControllerTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    LgdDigitalComparisonControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void returnsDiffsEvenWhenAgentFails() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/lgd-digital/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("model-change")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyName").value("LGD_DIGITAL"))
                .andExpect(jsonPath("$.diffs", hasSize(3)))
                .andExpect(jsonPath("$.diffs[0].path").value("strategyResponse.lgdData.lgd"))
                .andExpect(jsonPath("$.summary.totalDiffs").value(3))
                .andExpect(jsonPath("$.summary.deterministicSeverity").value("WARNING"))
                .andExpect(jsonPath("$.summary.highestSeverity").doesNotExist())
                .andExpect(jsonPath("$.agentAnalysis.status").value("FAILED"))
                .andExpect(jsonPath("$.agentAnalysis.tokenUsage.inputTokens").value(0))
                .andExpect(jsonPath("$.agentAnalysis.tokenUsage.outputTokens").value(0))
                .andExpect(jsonPath("$.agentAnalysis.tokenUsage.totalTokens").value(0));
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/lgd-digital/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body must be valid JSON."));
    }

    @Test
    void rejectsMissingStrategyResponse() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("mainLaunch", objectMapper.createObjectNode());
        body.set("shadowLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/shadow.json"
        ));

        mockMvc.perform(post("/api/v1/strategies/lgd-digital/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("mainLaunch.strategyResponse object is required."));
    }

    private String requestBody(String fixtureName) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("mainLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/%s/main.json".formatted(fixtureName)
        ));
        body.set("shadowLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/%s/shadow.json".formatted(fixtureName)
        ));
        body.set("metadata", objectMapper.createObjectNode()
                .put("requestId", "REQ-1")
                .put("mainLaunchId", "MAIN-1")
                .put("shadowLaunchId", "SHADOW-1")
                .put("mainStrategyVersion", "2022")
                .put("shadowStrategyVersion", "2023"));
        return objectMapper.writeValueAsString(body);
    }

    @TestConfiguration
    static class ThrowingAgentConfiguration {
        @Bean
        @Primary
        AgentAnalyzer throwingAgentAnalyzer() {
            return input -> {
                throw new IllegalStateException("model unavailable");
            };
        }
    }
}
