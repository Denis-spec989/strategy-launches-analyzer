package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatStructuredCompletionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (dataset.toAbsolutePath().normalize().equals(results.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Calibration dataset and results must use different files.");
        }
        List<JudgeCalibrationCase> cases = new JudgeCalibrationDatasetValidator()
                .validate(JudgeCalibrationStore.readDataset(objectMapper, dataset));
        JudgeCalibrationStore store = new JudgeCalibrationStore(objectMapper, results);
        Map<String, JudgeCalibrationCase> latest = new LinkedHashMap<>(store.readLatest());
        SemanticJudge judge = new GigaChatSemanticJudge(completionClient, objectMapper, judgeModel);

        for (JudgeCalibrationCase item : cases) {
            JudgeCalibrationCase previous = latest.get(item.id());
            if (previous != null && previous.judgeGrade() != null
                    && (previous.judgeGrade().safetyPass() || previous.safetyAdjudication() != null)
                    && previous.sameCalibrationInput(item)) {
                continue;
            }
            SemanticGrade grade = judge.grade(item.input(), item.anonymizedAnalysis(), item.expectations());
            SafetyAdjudication adjudication = grade.safetyPass()
                    ? null
                    : judge.adjudicate(item.input(), item.anonymizedAnalysis(), item.expectations(), grade);
            JudgeCalibrationCase graded = item.withJudgeOutcome(grade, adjudication);
            store.append(graded);
            latest.put(item.id(), graded);
        }

        List<JudgeCalibrationCase> completed = new ArrayList<>();
        cases.forEach(item -> completed.add(latest.get(item.id())));
        JudgeCalibrationReport report = new JudgeCalibrationAnalyzer().analyze(
                completed,
                judgeModel,
                BenchmarkHashes.fileHash(dataset)
        );
        store.writeReport(report);
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
