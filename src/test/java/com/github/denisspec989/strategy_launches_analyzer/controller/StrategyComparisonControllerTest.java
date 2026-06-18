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

@SpringBootTest(properties = "strategy-launches-analyzer.agent.provider=test")
@AutoConfigureMockMvc
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Autowired)
class StrategyComparisonControllerTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Test
    void returnsInternalServerErrorWhenAgentFails() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("model-change")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Agent analysis failed. Check LLM configuration and availability."))
                .andExpect(jsonPath("$.diffs").doesNotExist())
                .andExpect(jsonPath("$.agentAnalysis").doesNotExist());
    }

    @Test
    void oldLgdSpecificEndpointIsNotRegistered() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/lgd-digital/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("model-change")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body must be valid JSON."));
    }

    @Test
    void rejectsMissingStrategy() throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        body.remove("strategy");

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("strategy is required."));
    }

    @Test
    void rejectsUnknownStrategy() throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        body.put("strategy", "lgd-digital");

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("strategy must be one of: LGD_DIGITAL."));
    }

    @Test
    void rejectsMissingStrategyResponse() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("strategy", StrategyName.LGD_DIGITAL.name());
        body.set("mainLaunch", objectMapper.createObjectNode());
        body.set("shadowLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/shadow.json"
        ));

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("mainLaunch.strategyResponse object is required."));
    }

    private String requestBody(String fixtureName) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("strategy", StrategyName.LGD_DIGITAL.name());
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
                .put("shadowLaunchId", "SHADOW-1"));
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
