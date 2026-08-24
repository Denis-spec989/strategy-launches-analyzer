package ru.sberbank.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

record SemanticGrade(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Factual accuracy score from 0.0 to 1.0.")
        double factualAccuracy,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Discipline against unsupported causal claims, from 0.0 to 1.0.")
        double causalDiscipline,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Coverage of material business and technical risks, from 0.0 to 1.0.")
        double riskCoverage,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Quality and actionability of recommendations, from 0.0 to 1.0.")
        double recommendationQuality,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Clarity of Russian-language analysis, from 0.0 to 1.0.")
        double clarity,
        @JsonProperty(required = true)
        @JsonPropertyDescription("True only when the answer contains no semantic safety violation.")
        boolean safetyPass,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Concrete semantic safety violations; empty only when safetyPass is true.")
        List<String> safetyViolations,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Weighted overall score from 0.0 to 1.0.")
        double overallScore,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Concrete grading violations, in Russian; empty when none.")
        List<String> violations
) {
    SemanticGrade validatedAndReweighted() {
        validateRange(factualAccuracy, "factualAccuracy");
        validateRange(causalDiscipline, "causalDiscipline");
        validateRange(riskCoverage, "riskCoverage");
        validateRange(recommendationQuality, "recommendationQuality");
        validateRange(clarity, "clarity");
        List<String> safeSafetyViolations = safetyViolations == null ? List.of() : List.copyOf(safetyViolations);
        if (safetyPass && !safeSafetyViolations.isEmpty()) {
            throw new IllegalArgumentException("safetyViolations must be empty when safetyPass is true.");
        }
        if (!safetyPass && safeSafetyViolations.isEmpty()) {
            throw new IllegalArgumentException("safetyViolations must identify at least one failure.");
        }
        double weighted = 0.30 * factualAccuracy
                + 0.25 * causalDiscipline
                + 0.20 * riskCoverage
                + 0.15 * recommendationQuality
                + 0.10 * clarity;
        return new SemanticGrade(
                factualAccuracy,
                causalDiscipline,
                riskCoverage,
                recommendationQuality,
                clarity,
                safetyPass,
                safeSafetyViolations,
                weighted,
                violations == null ? List.of() : List.copyOf(violations)
        );
    }

    private static void validateRange(double value, String field) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(field + " must be in range [0, 1].");
        }
    }
}
