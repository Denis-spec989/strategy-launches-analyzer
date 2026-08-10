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
        String error
) {
    BenchmarkSampleResult {
        tags = tags == null ? List.of() : List.copyOf(tags);
        corrections = corrections == null ? List.of() : List.copyOf(corrections);
    }

    String key() {
        return key(caseId, model, repetition);
    }

    static String key(String caseId, String model, int repetition) {
        return caseId + "\u001f" + model + "\u001f" + repetition;
    }
}
