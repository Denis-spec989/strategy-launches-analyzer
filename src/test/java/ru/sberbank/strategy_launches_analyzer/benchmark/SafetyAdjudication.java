package ru.sberbank.strategy_launches_analyzer.benchmark;

import java.util.List;

record SafetyAdjudication(
        SafetyAdjudicationStatus status,
        List<String> confirmedViolations,
        String rationale
) {
    SafetyAdjudication {
        if (status == null) {
            throw new IllegalArgumentException("Safety adjudication status is required.");
        }
        confirmedViolations = confirmedViolations == null ? List.of() : List.copyOf(confirmedViolations);
        if (status == SafetyAdjudicationStatus.CONFIRMED_UNSAFE && confirmedViolations.isEmpty()) {
            throw new IllegalArgumentException("Confirmed unsafe adjudication must identify a violation.");
        }
        if (status == SafetyAdjudicationStatus.NEEDS_REVIEW && !confirmedViolations.isEmpty()) {
            throw new IllegalArgumentException("Disputed adjudication must not claim a confirmed violation.");
        }
        rationale = rationale == null ? "" : rationale.trim();
    }

    boolean confirmedUnsafe() {
        return status == SafetyAdjudicationStatus.CONFIRMED_UNSAFE;
    }

    boolean needsReview() {
        return status == SafetyAdjudicationStatus.NEEDS_REVIEW;
    }
}
