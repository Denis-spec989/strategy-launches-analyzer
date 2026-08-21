package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentPostProcessingResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.GuardrailCorrection;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.GuardrailCorrectionType;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.RepairableAgentResponseReason;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssueType;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.RepairableAgentResponseException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class DefaultAgentAnalysisPostProcessor implements AgentAnalysisPostProcessor {
    private static final int MAX_RECOMMENDATIONS = 10;

    @Override
    public AgentPostProcessingResult process(
            StructuredAgentAnalysis raw,
            AgentAnalysisInput input,
            TokenUsage tokenUsage
    ) {
        validate(raw, input, tokenUsage);
        List<GuardrailCorrection> corrections = new ArrayList<>();
        List<DiffExplanation> explanations = withDeterministicDiffExplanations(
                input,
                raw.diffExplanations() == null ? List.of() : raw.diffExplanations(),
                corrections
        );
        Severity overallSeverity = finalSeverity(input, raw.overallSeverity(), explanations);
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
                null,
                null
        );
        return new AgentPostProcessingResult(analysis, corrections, tokenUsage);
    }

    private static void validate(
            StructuredAgentAnalysis response,
            AgentAnalysisInput input,
            TokenUsage tokenUsage
    ) {
        List<String> violations = new ArrayList<>();
        if (response == null) {
            violations.add("response is empty");
            throw invalidResponse(violations, null, tokenUsage);
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
            throw invalidResponse(violations, response, tokenUsage);
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
        Map<UUID, String> expectedDiffs = expectedDiffs(input);
        List<DiffExplanation> safeExplanations = explanations == null ? List.of() : explanations;
        if (safeExplanations.size() > expectedDiffs.size()) {
            violations.add("diffExplanations exceeds deterministic diff count");
        }

        Set<UUID> coveredDiffIds = new LinkedHashSet<>();
        for (int index = 0; index < safeExplanations.size(); index++) {
            DiffExplanation explanation = safeExplanations.get(index);
            if (explanation == null) {
                violations.add("diffExplanations[" + index + "] is null");
                continue;
            }
            if (explanation.diffId() == null) {
                violations.add("diffExplanations[" + index + "].diffId is null");
            }
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
        Map<UUID, DiffExplanation> byDiffId = new LinkedHashMap<>();
        Map<UUID, DiffEntry> diffsById = new LinkedHashMap<>();
        input.diffs().forEach(diff -> diffsById.put(diff.id(), diff));
        modelExplanations.forEach(explanation -> {
            DiffEntry diff = diffsById.get(explanation.diffId());
            if (diff != null && isBelow(explanation.severity(), diff.deterministicSeverity())) {
                corrections.add(new GuardrailCorrection(
                        GuardrailCorrectionType.DIFF_SEVERITY_ESCALATED,
                        explanation.diffId(),
                        "diff explanation severity raised to deterministic floor " + diff.deterministicSeverity()
                ));
                byDiffId.put(explanation.diffId(), withSeverity(explanation, diff.deterministicSeverity()));
            } else {
                byDiffId.put(explanation.diffId(), explanation);
            }
        });

        for (DiffEntry diff : input.diffs()) {
            if (byDiffId.containsKey(diff.id())) {
                continue;
            }
            List<ContractIssue> criticalIssues = criticalIssuesAtPath(input, diff.path());
            if (!criticalIssues.isEmpty()) {
                byDiffId.put(diff.id(), deterministicContractExplanation(diff, criticalIssues));
                corrections.add(new GuardrailCorrection(
                        GuardrailCorrectionType.CRITICAL_CONTRACT_EXPLANATION_ADDED,
                        diff.id(),
                        "added deterministic explanation for exact-path critical contract issue"
                ));
            } else if (diff.deterministicSeverity() == Severity.CRITICAL) {
                byDiffId.put(diff.id(), deterministicHardCriticalExplanation(diff));
                corrections.add(new GuardrailCorrection(
                        GuardrailCorrectionType.HARD_CRITICAL_EXPLANATION_ADDED,
                        diff.id(),
                        "added deterministic hard-critical explanation"
                ));
            } else {
                byDiffId.put(diff.id(), neutralWarningExplanation(diff));
                corrections.add(new GuardrailCorrection(
                        GuardrailCorrectionType.NON_CRITICAL_EXPLANATION_ADDED,
                        diff.id(),
                        "added warning explanation omitted by the model"
                ));
            }
        }

        List<DiffEntry> uncoveredWarningDiffs = input.diffs().stream()
                .filter(diff -> diff.deterministicSeverity() == Severity.WARNING)
                .filter(diff -> modelExplanations.stream().noneMatch(item -> item.diffId().equals(diff.id())))
                .toList();
        if (!uncoveredWarningDiffs.isEmpty()) {
            log.warn("Filled {} warning diffExplanations omitted by the model: diffIds={}",
                    uncoveredWarningDiffs.size(),
                    uncoveredWarningDiffs.stream().map(DiffEntry::id).limit(10).toList());
        }
        return input.diffs().stream().map(diff -> byDiffId.get(diff.id())).toList();
    }

    private static boolean isBelow(Severity actual, Severity floor) {
        return actual == null || actual.ordinal() < floor.ordinal();
    }

    private static DiffExplanation withSeverity(DiffExplanation explanation, Severity severity) {
        return new DiffExplanation(
                explanation.diffId(),
                explanation.path(),
                severity,
                explanation.explanation()
        );
    }

    private static DiffExplanation deterministicContractExplanation(
            DiffEntry diff,
            List<ContractIssue> criticalIssues
    ) {
        List<ContractIssue> missingRequired = criticalIssues.stream()
                .filter(issue -> issue.type() == ContractIssueType.REQUIRED_FIELD_MISSING)
                .toList();
        if (!missingRequired.isEmpty()) {
            String sides = missingRequired.stream()
                    .map(issue -> issue.side().name())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(", "));
            String expected = missingRequired.stream()
                    .map(ContractIssue::expected)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining("; "));
            return new DiffExplanation(
                    diff.id(),
                    diff.path(),
                    Severity.CRITICAL,
                    "Обязательное поле %s отсутствует на стороне %s. Ожидаемый контракт: %s. "
                            .formatted(diff.path(), sides, expected.isBlank() ? "required=true" : expected)
                            + "Это критичное нарушение совместимости, которое должно блокировать promotion до восстановления поля."
            );
        }
        return new DiffExplanation(
                diff.id(),
                diff.path(),
                Severity.CRITICAL,
                "Contract validation зафиксировала критичное нарушение для пути %s; promotion нужно блокировать "
                        .formatted(diff.path())
                        + "до восстановления совместимости с ожидаемым контрактом."
        );
    }

    private static List<ContractIssue> criticalIssuesAtPath(AgentAnalysisInput input, String path) {
        return input.contractValidation().stream()
                .filter(issue -> issue.severity() == Severity.CRITICAL)
                .filter(issue -> java.util.Objects.equals(issue.path(), path))
                .toList();
    }

    private static DiffExplanation deterministicHardCriticalExplanation(DiffEntry diff) {
        return new DiffExplanation(
                diff.id(),
                diff.path(),
                Severity.CRITICAL,
                "Детерминированная проверка пометила изменение как критическое: изменился технический, "
                        + "типовой или обязательный contract-level признак; promotion нужно блокировать до проверки совместимости."
        );
    }

    private static DiffExplanation neutralWarningExplanation(DiffEntry diff) {
        return new DiffExplanation(
                diff.id(),
                diff.path(),
                Severity.WARNING,
                "Изменение требует проверки; модель не предоставила отдельное объяснение."
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

    private static Severity finalSeverity(
            AgentAnalysisInput input,
            Severity modelSeverity,
            List<DiffExplanation> explanations
    ) {
        Severity result = maxSeverity(modelSeverity, input.summary().deterministicSeverity());
        for (ContractIssue issue : input.contractValidation()) {
            result = maxSeverity(result, issue.severity());
        }
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

    private static Map<UUID, String> expectedDiffs(AgentAnalysisInput input) {
        Map<UUID, String> expectedDiffs = new LinkedHashMap<>();
        input.diffs().forEach(diff -> expectedDiffs.put(diff.id(), diff.path()));
        return expectedDiffs;
    }

    private static RepairableAgentResponseException invalidResponse(
            List<String> violations,
            StructuredAgentAnalysis previousResponse,
            TokenUsage tokenUsage
    ) {
        return new RepairableAgentResponseException(
                "Invalid structured LLM response: " + String.join("; ", violations) + ".",
                RepairableAgentResponseReason.CONTRACT_VIOLATION,
                violations,
                previousResponse,
                tokenUsage
        );
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
