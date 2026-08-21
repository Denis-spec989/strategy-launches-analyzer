package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class JudgeCalibration {
    private static final double MIN_MARGIN = 0.20;
    private static final List<String> CALIBRATION_CASES =
            List.of("identical-basic", "metric-increase", "mode-critical");

    void calibrate(
            SemanticJudge judge,
            List<BenchmarkCase> cases,
            Map<String, AgentAnalysisInput> inputs
    ) {
        Map<String, BenchmarkCase> byId = cases.stream()
                .collect(Collectors.toMap(BenchmarkCase::id, Function.identity()));
        for (String caseId : CALIBRATION_CASES) {
            BenchmarkCase benchmarkCase = byId.get(caseId);
            AgentAnalysisInput input = inputs.get(caseId);
            if (benchmarkCase == null || input == null) {
                throw new IllegalStateException("Calibration case is missing: " + caseId);
            }
            SemanticGrade good = judge.grade(input, goodAnalysis(input, benchmarkCase.semantic()), benchmarkCase.semantic());
            SemanticGrade bad = judge.grade(input, badAnalysis(input, benchmarkCase.semantic()), benchmarkCase.semantic());
            if (good.overallScore() - bad.overallScore() < MIN_MARGIN) {
                throw new IllegalStateException(
                        "Semantic judge calibration failed for %s: good=%.3f, bad=%.3f, required margin=%.2f"
                                .formatted(caseId, good.overallScore(), bad.overallScore(), MIN_MARGIN)
                );
            }
        }
    }

    private static AgentAnalysis goodAnalysis(AgentAnalysisInput input, SemanticExpectations expectations) {
        Severity severity = input.summary().deterministicSeverity();
        String facts = joinOrDefault(expectations.requiredFacts(), "Детерминированных отличий не обнаружено.");
        String actions = joinOrDefault(expectations.expectedActions(), "Продолжить штатный контроль результатов.");
        List<DiffExplanation> explanations = input.diffs().stream()
                .map(diff -> new DiffExplanation(
                        diff.id(),
                        diff.path(),
                        diff.deterministicSeverity(),
                        "Зафиксировано детерминированное изменение поля " + diff.path() + "."
                ))
                .toList();
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                severity,
                facts,
                facts,
                severity == Severity.CRITICAL
                        ? "Обнаружен критический риск контракта или режима расчета."
                        : "Критических технических нарушений не обнаружено.",
                List.of(actions),
                explanations,
                null,
                null
        );
    }

    private static AgentAnalysis badAnalysis(AgentAnalysisInput input, SemanticExpectations expectations) {
        String forbidden = joinOrDefault(
                expectations.forbiddenConclusions(),
                "Shadow-запуск доказанно вызвал изменение всех кредитных решений."
        );
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                Severity.INFO,
                "Отличий нет.",
                forbidden,
                "Рисков нет.",
                List.of("Ничего не проверять и сразу продвигать shadow-запуск."),
                List.of(),
                null,
                null
        );
    }

    private static String joinOrDefault(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? fallback : String.join(". ", values);
    }
}
