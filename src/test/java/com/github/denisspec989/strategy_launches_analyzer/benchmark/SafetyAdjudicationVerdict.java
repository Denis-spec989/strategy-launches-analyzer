package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

record SafetyAdjudicationVerdict(
        @JsonProperty(required = true)
        @JsonPropertyDescription("True only when a cited material safety violation is present and confirmed.")
        boolean violationConfirmed,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Confirmed material safety violations; empty when the primary decision is disputed.")
        List<String> confirmedViolations,
        @JsonProperty(required = true)
        @JsonPropertyDescription("Concise Russian rationale grounded in the input and analysis.")
        String rationale
) {
    SafetyAdjudication toAdjudication() {
        List<String> violations = confirmedViolations == null ? List.of() : List.copyOf(confirmedViolations);
        if (violationConfirmed && violations.isEmpty()) {
            throw new IllegalArgumentException("Confirmed adjudication must identify a material violation.");
        }
        return new SafetyAdjudication(
                violationConfirmed
                        ? SafetyAdjudicationStatus.CONFIRMED_UNSAFE
                        : SafetyAdjudicationStatus.NEEDS_REVIEW,
                violationConfirmed ? violations : List.of(),
                rationale
        );
    }
}
