package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class JudgeCalibrationRunner {
    private final ObjectMapper objectMapper;
    private final Function<String, SemanticJudge> judgeFactory;

    JudgeCalibrationRunner(ObjectMapper objectMapper, Function<String, SemanticJudge> judgeFactory) {
        this.objectMapper = objectMapper;
        this.judgeFactory = judgeFactory;
    }

    JudgeCalibrationReport run(Path dataset, Path results, String judgeModel) {
        Path normalizedDataset = dataset.toAbsolutePath().normalize();
        Path normalizedResults = results.toAbsolutePath().normalize();
        if (normalizedDataset.equals(normalizedResults)) {
            throw new IllegalArgumentException("Calibration dataset and results must use different files.");
        }
        List<JudgeCalibrationCase> cases = new JudgeCalibrationDatasetValidator()
                .validate(JudgeCalibrationStore.readDataset(objectMapper, normalizedDataset));
        JudgeCalibrationStore store = new JudgeCalibrationStore(objectMapper, normalizedResults);
        Map<String, JudgeCalibrationCase> latest = new LinkedHashMap<>(store.readLatest());
        SemanticJudge judge = judgeFactory.apply(judgeModel);

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
                BenchmarkHashes.fileHash(normalizedDataset)
        );
        store.writeReport(report);
        return report;
    }
}
