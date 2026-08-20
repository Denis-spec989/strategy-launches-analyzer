package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import chat.giga.client.GigaChatClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentModelClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatStructuredCompletionClient;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AutomatedModelSelectionIT {
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgentModelClient modelClient;
    @Autowired private AgentAnalysisPostProcessor postProcessor;
    @Autowired private StrategyDiffEngine diffEngine;
    @Autowired private StrategyContractRegistry contractRegistry;
    @Autowired private GigaChatClient gigaChatClient;
    @Autowired private GigaChatStructuredCompletionClient completionClient;

    @Test
    void runsPilotFullBenchmarkAndAlternateJudgeReview() {
        AutomatedModelSelectionReport report = new AutomatedModelSelectionRunner(
                objectMapper,
                modelClient,
                postProcessor,
                new BenchmarkInputFactory(diffEngine, contractRegistry),
                model -> new GigaChatSemanticJudge(completionClient, objectMapper, model),
                () -> gigaChatClient.models().data().stream()
                        .filter(model -> "chat".equalsIgnoreCase(model.type()))
                        .map(chat.giga.model.Model::id)
                        .toList()
        ).run(AutomatedBenchmarkConfiguration.fromSystemProperties());

        assertThat(report.status()).isIn("SELECTED", "REVIEW_REQUIRED");
    }
}
