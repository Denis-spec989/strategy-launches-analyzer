package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.GuardrailCorrection;

import java.util.List;

record BenchmarkSampleResult(
        String caseId,
        List<String> tags,
        String model,
        int repetition,
        BenchmarkSampleStatus status,
        AgentModelCallResult callResult,
        DeterministicGrade rawGrade,
        AgentAnalysis finalAnalysis,
        List<GuardrailCorrection> corrections,
        DeterministicGrade finalGrade,
        SemanticGrade semanticGrade,
        SafetyAdjudication safetyAdjudication,
        String error
) {
    BenchmarkSampleResult {
        tags = tags == null ? List.of() : List.copyOf(tags);
        corrections = corrections == null ? List.of() : List.copyOf(corrections);
    }

    BenchmarkSampleResult(
            String caseId,
            List<String> tags,
            String model,
            int repetition,
            BenchmarkSampleStatus status,
            AgentModelCallResult callResult,
            DeterministicGrade rawGrade,
            AgentAnalysis finalAnalysis,
            List<GuardrailCorrection> corrections,
            DeterministicGrade finalGrade,
            SemanticGrade semanticGrade,
            String error
    ) {
        this(caseId, tags, model, repetition, status, callResult, rawGrade, finalAnalysis,
                corrections, finalGrade, semanticGrade, null, error);
    }

    String key() {
        return key(caseId, model, repetition);
    }

    static String key(String caseId, String model, int repetition) {
        return caseId + "\u001f" + model + "\u001f" + repetition;
    }

    boolean confirmedSafetyPass() {
        return semanticGrade != null
                && (semanticGrade.safetyPass()
                || (safetyAdjudication != null && !safetyAdjudication.confirmedUnsafe()));
    }

    boolean safetyNeedsReview() {
        return semanticGrade != null
                && !semanticGrade.safetyPass()
                && safetyAdjudication != null
                && safetyAdjudication.needsReview();
    }
}
