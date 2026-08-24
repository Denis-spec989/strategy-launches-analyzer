package ru.sberbank.strategy_launches_analyzer.benchmark;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import ru.sberbank.strategy_launches_analyzer.dto.agent.DiffExplanation;
import ru.sberbank.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class DeterministicAgentGrader {
    private static final int MAX_RECOMMENDATIONS = 10;
    DeterministicGrade gradeRaw(StructuredAgentAnalysis raw, AgentAnalysisInput input) {
        if (raw == null) {
            return failed("raw response is null", input);
        }
        List<String> violations = new ArrayList<>();
        List<String> languageViolations = new ArrayList<>();
        validateRequiredText(raw.summary(), raw.businessImpact(), raw.technicalRisks(), violations);
        validateRecommendations(raw.recommendations(), violations);
        int covered = validateExplanations(raw.diffExplanations(), input, violations);
        validateSeverity(raw.overallSeverity(), raw.diffExplanations(), input, violations);
        validateLanguage(narratives(raw), languageViolations);
        return grade(violations, languageViolations, input.diffs().size(), covered);
    }

    DeterministicGrade gradeFinal(AgentAnalysis analysis, AgentAnalysisInput input) {
        if (analysis == null) {
            return failed("final analysis is null", input);
        }
        List<String> violations = new ArrayList<>();
        List<String> languageViolations = new ArrayList<>();
        validateRequiredText(analysis.summary(), analysis.businessImpact(), analysis.technicalRisks(), violations);
        validateRecommendations(analysis.recommendations(), violations);
        int covered = validateExplanations(analysis.diffExplanations(), input, violations);
        validateSeverity(analysis.overallSeverity(), analysis.diffExplanations(), input, violations);
        validateLanguage(narratives(analysis), languageViolations);
        return grade(violations, languageViolations, input.diffs().size(), covered);
    }

    private static DeterministicGrade grade(
            List<String> violations,
            List<String> languageViolations,
            int expected,
            int covered
    ) {
        return new DeterministicGrade(
                violations.isEmpty(),
                languageViolations.isEmpty(),
                violations,
                languageViolations,
                expected,
                covered
        );
    }

    private static DeterministicGrade failed(String violation, AgentAnalysisInput input) {
        return new DeterministicGrade(false, false, List.of(violation), List.of(), input.diffs().size(), 0);
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
            List<String> violations
    ) {
        Map<UUID, String> expected = new LinkedHashMap<>();
        input.diffs().forEach(diff -> expected.put(diff.id(), diff.path()));
        Map<UUID, Severity> floors = new LinkedHashMap<>();
        input.diffs().forEach(diff -> floors.put(diff.id(), diff.deterministicSeverity()));
        Set<UUID> covered = new LinkedHashSet<>();
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
            Severity floor = floors.get(explanation.diffId());
            if (explanation.severity() != null && explanation.severity().ordinal() < floor.ordinal()) {
                violations.add("diff severity is below deterministic floor for " + explanation.diffId());
            }
            requireNonBlank(explanation.explanation(), "explanation for " + explanation.diffId(), violations);
        }
        Set<UUID> required = new LinkedHashSet<>(expected.keySet());
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
        if (input.summary().deterministicSeverity() == Severity.CRITICAL && overall != Severity.CRITICAL) {
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

    private static Map<String, String> narratives(StructuredAgentAnalysis raw) {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("summary", raw.summary());
        texts.put("businessImpact", raw.businessImpact());
        texts.put("technicalRisks", raw.technicalRisks());
        if (raw.recommendations() != null) {
            for (int index = 0; index < raw.recommendations().size(); index++) {
                texts.put("recommendations[" + index + "]", raw.recommendations().get(index));
            }
        }
        if (raw.diffExplanations() != null) {
            for (int index = 0; index < raw.diffExplanations().size(); index++) {
                DiffExplanation explanation = raw.diffExplanations().get(index);
                if (explanation != null) {
                    texts.put("diffExplanations[" + index + "].explanation", explanation.explanation());
                }
            }
        }
        return texts;
    }

    private static Map<String, String> narratives(AgentAnalysis analysis) {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("summary", analysis.summary());
        texts.put("businessImpact", analysis.businessImpact());
        texts.put("technicalRisks", analysis.technicalRisks());
        if (analysis.recommendations() != null) {
            for (int index = 0; index < analysis.recommendations().size(); index++) {
                texts.put("recommendations[" + index + "]", analysis.recommendations().get(index));
            }
        }
        if (analysis.diffExplanations() != null) {
            for (int index = 0; index < analysis.diffExplanations().size(); index++) {
                DiffExplanation explanation = analysis.diffExplanations().get(index);
                if (explanation != null) {
                    texts.put("diffExplanations[" + index + "].explanation", explanation.explanation());
                }
            }
        }
        return texts;
    }

    private static void validateLanguage(Map<String, String> narratives, List<String> violations) {
        for (Map.Entry<String, String> entry : narratives.entrySet()) {
            String text = entry.getValue();
            if (text == null || text.isBlank()) {
                continue;
            }
            boolean hasCyrillic = false;
            for (int index = 0; index < text.length(); index++) {
                char symbol = text.charAt(index);
                Character.UnicodeBlock block = Character.UnicodeBlock.of(symbol);
                if (block == Character.UnicodeBlock.CYRILLIC
                        || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                        || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_A
                        || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_B) {
                    hasCyrillic = true;
                    break;
                }
            }
            if (!hasCyrillic) {
                violations.add(entry.getKey() + " contains no Cyrillic text");
            }
        }
    }

    private static void requireNonBlank(String value, String field, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add(field + " is blank");
        }
    }
}
