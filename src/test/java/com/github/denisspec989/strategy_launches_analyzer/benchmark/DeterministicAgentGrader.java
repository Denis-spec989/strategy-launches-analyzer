package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DeterministicSeverityCalculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DeterministicAgentGrader {
    private static final int MAX_RECOMMENDATIONS = 10;
    private static final double MIN_CYRILLIC_RATIO = 0.60;

    DeterministicGrade gradeRaw(StructuredAgentAnalysis raw, AgentAnalysisInput input) {
        if (raw == null) {
            return new DeterministicGrade(false, List.of("raw response is null"), input.diffs().size(), 0, 0);
        }
        List<String> violations = new ArrayList<>();
        validateRequiredText(raw.summary(), raw.businessImpact(), raw.technicalRisks(), violations);
        validateRecommendations(raw.recommendations(), violations);
        int covered = validateExplanations(raw.diffExplanations(), input, false, violations);
        validateSeverity(raw.overallSeverity(), raw.diffExplanations(), input, violations);
        double ratio = cyrillicRatio(texts(raw));
        if (ratio < MIN_CYRILLIC_RATIO) {
            violations.add("Russian text ratio %.3f is below %.2f".formatted(ratio, MIN_CYRILLIC_RATIO));
        }
        return grade(violations, input.diffs().size(), covered, ratio);
    }

    DeterministicGrade gradeFinal(AgentAnalysis analysis, AgentAnalysisInput input) {
        if (analysis == null) {
            return new DeterministicGrade(false, List.of("final analysis is null"), input.diffs().size(), 0, 0);
        }
        List<String> violations = new ArrayList<>();
        validateRequiredText(analysis.summary(), analysis.businessImpact(), analysis.technicalRisks(), violations);
        validateRecommendations(analysis.recommendations(), violations);
        int covered = validateExplanations(analysis.diffExplanations(), input, true, violations);
        validateSeverity(analysis.overallSeverity(), analysis.diffExplanations(), input, violations);
        double ratio = cyrillicRatio(texts(analysis));
        if (ratio < MIN_CYRILLIC_RATIO) {
            violations.add("Russian text ratio %.3f is below %.2f".formatted(ratio, MIN_CYRILLIC_RATIO));
        }
        return grade(violations, input.diffs().size(), covered, ratio);
    }

    private static DeterministicGrade grade(
            List<String> violations,
            int expected,
            int covered,
            double cyrillicRatio
    ) {
        return new DeterministicGrade(violations.isEmpty(), violations, expected, covered, cyrillicRatio);
    }

    private static void validateRequiredText(
            String summary,
            String businessImpact,
            String technicalRisks,
            List<String> violations
    ) {
        requireNonBlank(summary, "summary", violations);
        requireNonBlank(businessImpact, "businessImpact", violations);
        requireNonBlank(technicalRisks, "technicalRisks", violations);
    }

    private static void validateRecommendations(List<String> recommendations, List<String> violations) {
        if (recommendations == null) {
            violations.add("recommendations is null");
            return;
        }
        if (recommendations.size() > MAX_RECOMMENDATIONS) {
            violations.add("recommendations exceeds max size " + MAX_RECOMMENDATIONS);
        }
        for (int index = 0; index < recommendations.size(); index++) {
            requireNonBlank(recommendations.get(index), "recommendations[" + index + "]", violations);
        }
    }

    private static int validateExplanations(
            List<DiffExplanation> explanations,
            AgentAnalysisInput input,
            boolean requireAllDiffs,
            List<String> violations
    ) {
        Map<String, String> expected = new LinkedHashMap<>();
        input.diffs().forEach(diff -> expected.put(diff.id(), diff.path()));
        Set<String> hardCritical = new LinkedHashSet<>();
        input.diffs().stream()
                .filter(DeterministicSeverityCalculator::isHardCriticalDiff)
                .forEach(diff -> hardCritical.add(diff.id()));
        Set<String> covered = new LinkedHashSet<>();
        if (explanations == null) {
            violations.add("diffExplanations is null");
        }
        List<DiffExplanation> safe = explanations == null ? List.of() : explanations;
        for (int index = 0; index < safe.size(); index++) {
            DiffExplanation explanation = safe.get(index);
            if (explanation == null) {
                violations.add("diffExplanations[" + index + "] is null");
                continue;
            }
            String expectedPath = expected.get(explanation.diffId());
            if (expectedPath == null) {
                violations.add("fabricated diffId " + explanation.diffId());
                continue;
            }
            if (!covered.add(explanation.diffId())) {
                violations.add("duplicated diffId " + explanation.diffId());
            }
            if (!expectedPath.equals(explanation.path())) {
                violations.add("path mismatch for " + explanation.diffId());
            }
            if (explanation.severity() == null) {
                violations.add("severity is null for " + explanation.diffId());
            }
            if (hardCritical.contains(explanation.diffId()) && explanation.severity() != Severity.CRITICAL) {
                violations.add("hard-critical diff is not CRITICAL: " + explanation.diffId());
            }
            requireNonBlank(explanation.explanation(), "explanation for " + explanation.diffId(), violations);
        }
        Set<String> required = new LinkedHashSet<>(expected.keySet());
        if (!requireAllDiffs) {
            required.removeAll(hardCritical);
        }
        required.removeAll(covered);
        if (!required.isEmpty()) {
            violations.add("missing diff explanations: " + required);
        }
        if (safe.size() > expected.size()) {
            violations.add("diff explanations exceed deterministic diff count");
        }
        return covered.size();
    }

    private static void validateSeverity(
            Severity overall,
            List<DiffExplanation> explanations,
            AgentAnalysisInput input,
            List<String> violations
    ) {
        if (overall == null) {
            violations.add("overallSeverity is null");
            return;
        }
        if (DeterministicSeverityCalculator.hasCriticalSignal(input.diffs(), input.contractValidation())
                && overall != Severity.CRITICAL) {
            violations.add("overallSeverity does not preserve deterministic CRITICAL");
        }
        if (input.diffs().isEmpty() && input.contractValidation().isEmpty() && overall != Severity.INFO) {
            violations.add("identical launches must have INFO severity");
        }
        if (explanations != null && explanations.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(explanation -> explanation.severity() == Severity.CRITICAL)
                && overall != Severity.CRITICAL) {
            violations.add("overallSeverity is lower than a CRITICAL diff explanation");
        }
    }

    private static List<String> texts(StructuredAgentAnalysis raw) {
        List<String> texts = new ArrayList<>();
        texts.add(raw.summary());
        texts.add(raw.businessImpact());
        texts.add(raw.technicalRisks());
        if (raw.recommendations() != null) {
            texts.addAll(raw.recommendations());
        }
        if (raw.diffExplanations() != null) {
            raw.diffExplanations().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(DiffExplanation::explanation)
                    .forEach(texts::add);
        }
        return texts;
    }

    private static List<String> texts(AgentAnalysis analysis) {
        List<String> texts = new ArrayList<>();
        texts.add(analysis.summary());
        texts.add(analysis.businessImpact());
        texts.add(analysis.technicalRisks());
        if (analysis.recommendations() != null) {
            texts.addAll(analysis.recommendations());
        }
        if (analysis.diffExplanations() != null) {
            analysis.diffExplanations().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(DiffExplanation::explanation)
                    .forEach(texts::add);
        }
        return texts;
    }

    private static double cyrillicRatio(List<String> texts) {
        long letters = 0;
        long cyrillic = 0;
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            for (int index = 0; index < text.length(); index++) {
                char symbol = text.charAt(index);
                if (Character.isLetter(symbol)) {
                    letters++;
                    Character.UnicodeBlock block = Character.UnicodeBlock.of(symbol);
                    if (block == Character.UnicodeBlock.CYRILLIC
                            || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                            || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_A
                            || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_B) {
                        cyrillic++;
                    }
                }
            }
        }
        return letters == 0 ? 0 : (double) cyrillic / letters;
    }

    private static void requireNonBlank(String value, String field, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add(field + " is blank");
        }
    }
}
