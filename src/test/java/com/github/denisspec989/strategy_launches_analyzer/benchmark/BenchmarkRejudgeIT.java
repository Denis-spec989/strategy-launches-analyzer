package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.ai.openai.api-key=${OPENAI_API_KEY:rejudge-key-missing}",
                "spring.ai.openai.chat.api-key=${OPENAI_API_KEY:rejudge-key-missing}"
        }
)
@ActiveProfiles("openai")
class BenchmarkRejudgeIT {
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StrategyDiffEngine diffEngine;
    @Autowired private StrategyContractRegistry contractRegistry;
    @Autowired private ChatClient.Builder chatClientBuilder;

    @Test
    void rejudgesSavedCandidateResponsesWithoutCandidateCalls() {
        String judgeModel = requiredProperty("benchmark-rejudge.judge-model");
        Path calibrationDataset = Path.of(requiredProperty("benchmark-rejudge.judge-calibration-dataset"));
        JudgeCalibrationGate.requireAccepted(
                objectMapper,
                Path.of(requiredProperty("benchmark-rejudge.judge-calibration-report")),
                calibrationDataset,
                judgeModel
        );
        BenchmarkSummary summary = new SavedResponseRejudgeRunner(
                objectMapper,
                new OpenAiSemanticJudge(chatClientBuilder.build(), objectMapper, judgeModel),
                new BenchmarkInputFactory(diffEngine, contractRegistry)
        ).run(Path.of(requiredProperty("benchmark-rejudge.source")), judgeModel);

        assertThat(summary.models()).isNotEmpty();
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for saved-response rejudge.");
        }
        return value;
    }
}
