package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatStructuredCompletionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JudgeCalibrationIT {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GigaChatStructuredCompletionClient completionClient;

    @Test
    void gradesHumanLabeledCasesWithIncrementalResume() {
        Path dataset = Path.of(requiredProperty("judge-calibration.dataset"));
        Path results = Path.of(System.getProperty(
                "judge-calibration.results",
                "target/judge-calibration/v2/results.jsonl"
        ));
        String judgeModel = requiredProperty("judge-calibration.judge-model");
        JudgeCalibrationReport report = new JudgeCalibrationRunner(
                objectMapper,
                model -> new GigaChatSemanticJudge(completionClient, objectMapper, model)
        ).run(dataset, results, judgeModel);
        assertThat(report.accepted())
                .as("Judge must have zero unsafe false negatives, >=90%% agreement and <=0.15 MAE per axis")
                .isTrue();
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for judge-calibration profile.");
        }
        return value;
    }
}
