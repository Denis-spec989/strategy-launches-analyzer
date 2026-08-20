package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import chat.giga.client.GigaChatClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatStructuredCompletionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JudgeSelectionIT {
    @Autowired private ObjectMapper objectMapper;
    @Autowired private GigaChatClient gigaChatClient;
    @Autowired private GigaChatStructuredCompletionClient completionClient;

    @Test
    void calibratesAvailableCandidatesAndSelectsJudge() {
        JudgeSelectionReport report = new JudgeSelectionRunner(
                objectMapper,
                () -> gigaChatClient.models().data().stream()
                        .filter(model -> "chat".equalsIgnoreCase(model.type()))
                        .map(chat.giga.model.Model::id)
                        .toList(),
                new JudgeCalibrationRunner(
                        objectMapper,
                        model -> new GigaChatSemanticJudge(completionClient, objectMapper, model)
                )
        ).run(JudgeSelectionConfiguration.fromSystemProperties());

        assertThat(report.selectedJudgeModel())
                .as("At least one configured judge model must pass calibration")
                .isNotBlank();
    }
}
