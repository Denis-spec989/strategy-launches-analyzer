package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;

import java.util.List;

record JudgeCalibrationCase(
        String schemaVersion,
        String id,
        CalibrationCaseSource source,
        String anonymizedCohort,
        String scenario,
        List<String> coverageTags,
        AgentAnalysisInput input,
        SemanticExpectations expectations,
        AgentAnalysis anonymizedAnalysis,
        HumanCalibrationLabel humanLabel,
        SemanticGrade judgeGrade,
        SafetyAdjudication safetyAdjudication
) {
    static final String SCHEMA_VERSION = "judge-calibration/v2";

    JudgeCalibrationCase validatedForJudge() {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported calibration schemaVersion: " + schemaVersion);
        }
        if (id == null || id.isBlank() || source == null || scenario == null || scenario.isBlank()
                || input == null || expectations == null || anonymizedAnalysis == null || humanLabel == null) {
            throw new IllegalArgumentException("Calibration case is incomplete: " + id);
        }
        if (anonymizedAnalysis.tokenUsage() != null && anonymizedAnalysis.tokenUsage().model() != null) {
            throw new IllegalArgumentException("Calibration analysis must not expose model id: " + id);
        }
        if (input.metadata() != null) {
            throw new IllegalArgumentException("Calibration input must not expose launch metadata: " + id);
        }
        return new JudgeCalibrationCase(
                schemaVersion,
                id,
                source,
                anonymizedCohort,
                scenario.trim(),
                coverageTags == null ? List.of() : List.copyOf(coverageTags),
                input,
                expectations,
                anonymizedAnalysis,
                humanLabel.validated(),
                judgeGrade == null ? null : judgeGrade.validatedAndReweighted(),
                safetyAdjudication
        );
    }

    JudgeCalibrationCase withJudgeGrade(SemanticGrade grade) {
        return withJudgeOutcome(grade, null);
    }

    JudgeCalibrationCase withJudgeOutcome(SemanticGrade grade, SafetyAdjudication adjudication) {
        return new JudgeCalibrationCase(
                schemaVersion,
                id,
                source,
                anonymizedCohort,
                scenario,
                coverageTags,
                input,
                expectations,
                anonymizedAnalysis,
                humanLabel,
                grade.validatedAndReweighted(),
                adjudication
        );
    }

    boolean sameCalibrationInput(JudgeCalibrationCase other) {
        return other != null
                && java.util.Objects.equals(schemaVersion, other.schemaVersion)
                && java.util.Objects.equals(id, other.id)
                && source == other.source
                && java.util.Objects.equals(anonymizedCohort, other.anonymizedCohort)
                && java.util.Objects.equals(scenario, other.scenario)
                && java.util.Objects.equals(coverageTags, other.coverageTags)
                && java.util.Objects.equals(input, other.input)
                && java.util.Objects.equals(expectations, other.expectations)
                && java.util.Objects.equals(anonymizedAnalysis, other.anonymizedAnalysis)
                && java.util.Objects.equals(humanLabel, other.humanLabel);
    }

    JudgeCalibrationCase(
            String schemaVersion,
            String id,
            CalibrationCaseSource source,
            String anonymizedCohort,
            String scenario,
            List<String> coverageTags,
            AgentAnalysisInput input,
            SemanticExpectations expectations,
            AgentAnalysis anonymizedAnalysis,
            HumanCalibrationLabel humanLabel,
            SemanticGrade judgeGrade
    ) {
        this(schemaVersion, id, source, anonymizedCohort, scenario, coverageTags, input, expectations,
                anonymizedAnalysis, humanLabel, judgeGrade, null);
    }
}
