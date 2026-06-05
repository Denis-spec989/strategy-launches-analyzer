package com.github.denisspec989.strategy_launches_analyzer.agent;

import com.github.denisspec989.strategy_launches_analyzer.domain.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.domain.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.domain.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffType;
import com.github.denisspec989.strategy_launches_analyzer.domain.Severity;
import com.github.denisspec989.strategy_launches_analyzer.domain.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class FallbackAgentAnalyzer implements AgentAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackAgentAnalyzer.class);

    @Override
    public AgentAnalysis analyze(AgentAnalysisInput input) {
        LOGGER.info("Fallback agent analysis started: requestId={}, strategyName={}, diffCount={}, contractIssueCount={}. LLM request is not performed in fallback mode.",
                requestId(input),
                input.strategyName(),
                input.diffs().size(),
                input.contractValidation().size());
        Severity severity = overallSeverity(input);
        AgentAnalysis analysis = new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                severity,
                summary(input),
                businessImpact(input),
                technicalRisks(input),
                recommendations(input),
                explanations(input),
                TokenUsage.zero(),
                null
        );
        LOGGER.info("Fallback agent analysis completed: requestId={}, status={}, severity={}",
                requestId(input),
                analysis.status(),
                analysis.overallSeverity());
        return analysis;
    }

    private static Severity overallSeverity(AgentAnalysisInput input) {
        boolean hasCriticalIssue = input.contractValidation().stream()
                .anyMatch(issue -> issue.severity() == Severity.CRITICAL);
        boolean hasCriticalDiff = input.diffs().stream()
                .anyMatch(FallbackAgentAnalyzer::isCriticalDiff);
        if (hasCriticalIssue || hasCriticalDiff) {
            return Severity.CRITICAL;
        }
        if (!input.diffs().isEmpty() || !input.contractValidation().isEmpty()) {
            return Severity.WARNING;
        }
        return Severity.INFO;
    }

    private static String summary(AgentAnalysisInput input) {
        if (input.diffs().isEmpty() && input.contractValidation().isEmpty()) {
            return "Main and shadow launches match by the LGD_DIGITAL v1 contract.";
        }
        return "Found %d deterministic diffs and %d contract validation issues for LGD_DIGITAL."
                .formatted(input.diffs().size(), input.contractValidation().size());
    }

    private static String businessImpact(AgentAnalysisInput input) {
        List<DiffEntry> diffs = input.diffs();
        boolean hasMetricDiff = diffs.stream().anyMatch(diff -> diff.category() == DiffCategory.METRIC);
        boolean hasModelDiff = diffs.stream().anyMatch(diff -> diff.category() == DiffCategory.MODEL);
        String metricDescriptions = descriptions(input, DiffCategory.METRIC);
        if (hasMetricDiff && hasModelDiff) {
            return "Shadow launch changed LGD metrics%s and the selected LGD model. The model change may explain the metric delta, but it should be confirmed by strategy traces or business rules."
                    .formatted(metricDescriptions.isBlank() ? "" : " (" + metricDescriptions + ")");
        }
        if (hasMetricDiff) {
            return "Shadow launch changed LGD metrics%s. Review absolute and relative deltas before promoting the shadow logic."
                    .formatted(metricDescriptions.isBlank() ? "" : " (" + metricDescriptions + ")");
        }
        if (hasModelDiff) {
            return "Shadow launch selected a different LGD model while metrics may or may not have changed.";
        }
        return "No direct LGD metric impact was detected from deterministic diffs.";
    }

    private static String technicalRisks(AgentAnalysisInput input) {
        boolean hasContractIssues = !input.contractValidation().isEmpty();
        boolean hasTechnicalDiffs = input.diffs().stream()
                .anyMatch(diff -> diff.category() == DiffCategory.CONTRACT_TECHNICAL);
        if (hasContractIssues && hasTechnicalDiffs) {
            return "The response has contract or shape changes. These must be resolved or explicitly approved before the shadow version can become main.";
        }
        if (hasContractIssues) {
            return "The response violates the declared LGD_DIGITAL contract.";
        }
        if (hasTechnicalDiffs) {
            return "The response changed technical/context fields. Check constants and serialization logic.";
        }
        return "No contract-level technical risk was detected.";
    }

    private static List<String> recommendations(AgentAnalysisInput input) {
        List<String> recommendations = new ArrayList<>();
        if (input.diffs().isEmpty() && input.contractValidation().isEmpty()) {
            recommendations.add("No action is required for this launch pair.");
            return List.copyOf(recommendations);
        }
        if (!input.contractValidation().isEmpty()) {
            recommendations.add("Fix or approve contract validation issues before promoting the shadow strategy.");
        }
        if (input.diffs().stream().anyMatch(diff -> diff.category() == DiffCategory.METRIC)) {
            recommendations.add("Validate LGD metric deltas against the expected strategy change.");
        }
        if (input.diffs().stream().anyMatch(diff -> diff.path().endsWith(".mode") || diff.path().endsWith(".type"))) {
            recommendations.add("Check response constants and mapping code for accidental regressions.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Review the deterministic diff list with the strategy owner.");
        }
        return List.copyOf(recommendations);
    }

    private static List<DiffExplanation> explanations(AgentAnalysisInput input) {
        return input.diffs().stream()
                .map(diff -> new DiffExplanation(
                        diff.id(),
                        diff.path(),
                        isCriticalDiff(diff) ? Severity.CRITICAL : Severity.WARNING,
                        explanation(diff, description(input, diff.path()))
                ))
                .toList();
    }

    private static String explanation(DiffEntry diff, String fieldDescription) {
        String prefix = fieldDescription.isBlank() ? "" : "Field meaning: " + fieldDescription + ". ";
        if (diff.type() == DiffType.NUMERIC_VALUE_CHANGED) {
            return prefix + "Numeric value changed in shadow launch. Absolute and relative deltas are calculated by the Java diff engine.";
        }
        if (diff.category() == DiffCategory.MODEL) {
            return prefix + "LGD model changed in shadow launch. Treat this as a possible explanation for metric changes, not as proven root cause.";
        }
        if (diff.path().endsWith(".mode") || diff.path().endsWith(".type")) {
            return prefix + "Technical response field changed. This resembles a constant or mapping regression and should be checked.";
        }
        if (diff.type() == DiffType.FIELD_ADDED_IN_SHADOW || diff.type() == DiffType.FIELD_ADDED_IN_MAIN) {
            return prefix + "Response shape changed between main and shadow launches.";
        }
        if (diff.type() == DiffType.TYPE_MISMATCH || diff.type() == DiffType.NULLABILITY_VIOLATION) {
            return prefix + "Contract-level value problem detected.";
        }
        return prefix + "Deterministic value difference detected.";
    }

    private static String descriptions(AgentAnalysisInput input, DiffCategory category) {
        List<String> touchedPaths = input.diffs().stream()
                .filter(diff -> diff.category() == category)
                .map(DiffEntry::path)
                .distinct()
                .toList();
        return context(input).stream()
                .filter(context -> touchedPaths.contains(context.path()))
                .map(ContractFieldContext::description)
                .filter(description -> description != null && !description.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String description(AgentAnalysisInput input, String path) {
        return context(input).stream()
                .filter(context -> context.path().equals(path))
                .map(ContractFieldContext::description)
                .filter(description -> description != null && !description.isBlank())
                .findFirst()
                .orElse("");
    }

    private static List<ContractFieldContext> context(AgentAnalysisInput input) {
        return input.contractContext() == null ? List.of() : input.contractContext();
    }

    private static boolean isCriticalDiff(DiffEntry diff) {
        return diff.type() == DiffType.TYPE_MISMATCH
                || diff.type() == DiffType.NULLABILITY_VIOLATION
                || diff.type() == DiffType.REQUIRED_FIELD_MISSING
                || diff.path().endsWith(".mode")
                || diff.path().endsWith(".type");
    }

    private static String requestId(AgentAnalysisInput input) {
        if (input.metadata() == null || input.metadata().requestId() == null || input.metadata().requestId().isBlank()) {
            return "not-provided";
        }
        return input.metadata().requestId();
    }
}
