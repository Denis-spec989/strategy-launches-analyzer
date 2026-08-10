package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentPostProcessingResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.GuardrailCorrection;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.GuardrailCorrectionType;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DeterministicSeverityCalculator;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class DefaultAgentAnalysisPostProcessor implements AgentAnalysisPostProcessor {
    private static final int MAX_RECOMMENDATIONS = 10;

    @Override
    public AgentPostProcessingResult process(
            StructuredAgentAnalysis raw,
            AgentAnalysisInput input,
            TokenUsage tokenUsage
    ) {
        validate(raw, input);
        List<GuardrailCorrection> corrections = new ArrayList<>();
        List<DiffExplanation> explanations = withDeterministicDiffExplanations(
                input,
                raw.diffExplanations() == null ? List.of() : raw.diffExplanations(),
                corrections
        );
        Severity guardedSeverity = applyHardCriticalGuardrail(input, raw.overallSeverity());
        Severity overallSeverity = escalateToExplanationSeverity(guardedSeverity, explanations);
        if (overallSeverity != raw.overallSeverity()) {
            corrections.add(new GuardrailCorrection(
                    GuardrailCorrectionType.OVERALL_SEVERITY_CHANGED,
                    null,
                    "overallSeverity changed from %s to %s".formatted(raw.overallSeverity(), overallSeverity)
            ));
        }
        String technicalRisks = raw.technicalRisks().trim();
        long criticalIssueCount = criticalContractIssueCount(input);
        if (criticalIssueCount > 0) {
            technicalRisks = appendCriticalContractIssueRisk(technicalRisks, criticalIssueCount);
            corrections.add(new GuardrailCorrection(
                    GuardrailCorrectionType.CRITICAL_CONTRACT_RISK_APPENDED,
                    null,
                    "appended deterministic risk for %d critical contract issues".formatted(criticalIssueCount)
            ));
        }

        AgentAnalysis analysis = new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                overallSeverity,
                raw.summary().trim(),
                raw.businessImpact().trim(),
                technicalRisks,
                copyTrimmed(raw.recommendations()),
                explanations,
                tokenUsage == null ? TokenUsage.zero() : tokenUsage,
                null
        );
        return new AgentPostProcessingResult(analysis, corrections);
    }

    private static void validate(StructuredAgentAnalysis response, AgentAnalysisInput input) {
        List<String> violations = new ArrayList<>();
        if (response == null) {
            violations.add("response is empty");
            throw invalidResponse(violations);
        }
        if (response.overallSeverity() == null) {
            violations.add("overallSeverity is null");
        }
        requireNonBlank(response.summary(), "summary", violations);
        requireNonBlank(response.businessImpact(), "businessImpact", violations);
        requireNonBlank(response.technicalRisks(), "technicalRisks", violations);
        validateRecommendations(response.recommendations(), violations);
        validateDiffExplanations(response.diffExplanations(), input, violations);
        if (!violations.isEmpty()) {
            throw invalidResponse(violations);
        }
    }

    private static void validateRecommendations(List<String> recommendations, List<String> violations) {
        if (recommendations == null) {
            return;
        }
        if (recommendations.size() > MAX_RECOMMENDATIONS) {
            violations.add("recommendations exceeds max size " + MAX_RECOMMENDATIONS);
        }
        for (int index = 0; index < recommendations.size(); index++) {
            if (isBlank(recommendations.get(index))) {
                violations.add("recommendations[" + index + "] is blank");
            }
        }
    }

    private static void validateDiffExplanations(
            List<DiffExplanation> explanations,
            AgentAnalysisInput input,
            List<String> violations
    ) {
        Map<String, String> expectedDiffs = expectedDiffs(input);
        List<DiffExplanation> safeExplanations = explanations == null ? List.of() : explanations;
        if (safeExplanations.size() > expectedDiffs.size()) {
            violations.add("diffExplanations exceeds deterministic diff count");
        }

        Set<String> coveredDiffIds = new LinkedHashSet<>();
        for (int index = 0; index < safeExplanations.size(); index++) {
            DiffExplanation explanation = safeExplanations.get(index);
            if (explanation == null) {
                violations.add("diffExplanations[" + index + "] is null");
                continue;
            }
            requireNonBlank(explanation.diffId(), "diffExplanations[" + index + "].diffId", violations);
            requireNonBlank(explanation.path(), "diffExplanations[" + index + "].path", violations);
            if (explanation.severity() == null) {
                violations.add("diffExplanations[" + index + "].severity is null");
            }
            requireNonBlank(explanation.explanation(), "diffExplanations[" + index + "].explanation", violations);

            String expectedPath = expectedDiffs.get(explanation.diffId());
            if (expectedPath == null) {
                violations.add("diffExplanations[" + index + "].diffId is not in deterministic diffs");
                continue;
            }
            if (!expectedPath.equals(explanation.path())) {
                violations.add("diffExplanations[" + index + "].path does not match deterministic diff");
            }
            if (!coveredDiffIds.add(explanation.diffId())) {
                violations.add("diffExplanations[" + index + "].diffId is duplicated");
            }
        }
    }

    private static List<DiffExplanation> withDeterministicDiffExplanations(
            AgentAnalysisInput input,
            List<DiffExplanation> modelExplanations,
            List<GuardrailCorrection> corrections
    ) {
        Map<String, DiffExplanation> byDiffId = new LinkedHashMap<>();
        Map<String, DiffEntry> hardCriticalDiffs = new LinkedHashMap<>();
        input.diffs().stream()
                .filter(DeterministicSeverityCalculator::isHardCriticalDiff)
                .forEach(diff -> hardCriticalDiffs.put(diff.id(), diff));
        modelExplanations.forEach(explanation -> {
            if (hardCriticalDiffs.containsKey(explanation.diffId()) && explanation.severity() != Severity.CRITICAL) {
                corrections.add(new GuardrailCorrection(
                        GuardrailCorrectionType.DIFF_SEVERITY_ESCALATED,
                        explanation.diffId(),
                        "hard-critical diff explanation severity changed to CRITICAL"
                ));
                byDiffId.put(explanation.diffId(), withCriticalSeverity(explanation));
            } else {
                byDiffId.put(explanation.diffId(), explanation);
            }
        });
        input.diffs().stream()
                .filter(DeterministicSeverityCalculator::isHardCriticalDiff)
                .filter(diff -> !byDiffId.containsKey(diff.id()))
                .forEach(diff -> {
                    byDiffId.put(diff.id(), deterministicHardCriticalExplanation(diff));
                    corrections.add(new GuardrailCorrection(
                            GuardrailCorrectionType.HARD_CRITICAL_EXPLANATION_ADDED,
                            diff.id(),
                            "added deterministic hard-critical explanation"
                    ));
                });

        List<DiffEntry> uncoveredNonCriticalDiffs = expectedNonCriticalDiffs(input).stream()
                .filter(diff -> !byDiffId.containsKey(diff.id()))
                .toList();
        uncoveredNonCriticalDiffs.forEach(diff -> {
            byDiffId.put(diff.id(), neutralNonCriticalExplanation(diff));
            corrections.add(new GuardrailCorrection(
                    GuardrailCorrectionType.NON_CRITICAL_EXPLANATION_ADDED,
                    diff.id(),
                    "added neutral explanation omitted by the model"
            ));
        });
        if (!uncoveredNonCriticalDiffs.isEmpty()) {
            log.warn("Filled {} non-critical diffExplanations omitted by the model with neutral stubs: diffIds={}",
                    uncoveredNonCriticalDiffs.size(),
                    uncoveredNonCriticalDiffs.stream().map(DiffEntry::id).limit(10).toList());
        }
        return List.copyOf(byDiffId.values());
    }

    private static DiffExplanation withCriticalSeverity(DiffExplanation explanation) {
        return new DiffExplanation(
                explanation.diffId(),
                explanation.path(),
                Severity.CRITICAL,
                explanation.explanation()
        );
    }

    private static DiffExplanation deterministicHardCriticalExplanation(DiffEntry diff) {
        return new DiffExplanation(
                diff.id(),
                diff.path(),
                Severity.CRITICAL,
                "Детерминированная проверка пометила изменение как критическое: изменился технический, "
                        + "типовой или обязательный contract-level признак."
        );
    }

    private static DiffExplanation neutralNonCriticalExplanation(DiffEntry diff) {
        return new DiffExplanation(
                diff.id(),
                diff.path(),
                Severity.WARNING,
                "Некритичное изменение; модель не предоставила отдельное объяснение."
        );
    }

    private static long criticalContractIssueCount(AgentAnalysisInput input) {
        return input.contractValidation().stream()
                .filter(issue -> issue.severity() == Severity.CRITICAL)
                .count();
    }

    private static String appendCriticalContractIssueRisk(String technicalRisks, long criticalIssueCount) {
        return technicalRisks + " Детерминированная contract validation нашла критические нарушения: "
                + criticalIssueCount + ". Их нужно исправить или явно согласовать до promotion.";
    }

    private static Severity applyHardCriticalGuardrail(AgentAnalysisInput input, Severity modelSeverity) {
        if (DeterministicSeverityCalculator.hasCriticalSignal(input.diffs(), input.contractValidation())) {
            return Severity.CRITICAL;
        }
        return modelSeverity;
    }

    private static Severity escalateToExplanationSeverity(Severity base, List<DiffExplanation> explanations) {
        Severity result = base;
        for (DiffExplanation explanation : explanations) {
            if (explanation != null) {
                result = maxSeverity(result, explanation.severity());
            }
        }
        return result;
    }

    private static Severity maxSeverity(Severity left, Severity right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    private static Map<String, String> expectedDiffs(AgentAnalysisInput input) {
        Map<String, String> expectedDiffs = new LinkedHashMap<>();
        input.diffs().forEach(diff -> expectedDiffs.put(diff.id(), diff.path()));
        return expectedDiffs;
    }

    private static List<DiffEntry> expectedNonCriticalDiffs(AgentAnalysisInput input) {
        return input.diffs().stream()
                .filter(diff -> !DeterministicSeverityCalculator.isHardCriticalDiff(diff))
                .toList();
    }

    private static AgentAnalysisException invalidResponse(List<String> violations) {
        return new AgentAnalysisException("Invalid structured LLM response: " + String.join("; ", violations) + ".");
    }

    private static void requireNonBlank(String value, String fieldName, List<String> violations) {
        if (isBlank(value)) {
            violations.add(fieldName + " is blank");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> copyTrimmed(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(String::trim).toList();
    }
}
