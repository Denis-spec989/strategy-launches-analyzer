package ru.sberbank.strategy_launches_analyzer.benchmark;

import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class JudgeCalibrationDatasetValidator {
    static final int MIN_CASES = 19;
    static final int MIN_REAL_CASES = 10;
    static final int MIN_UNSAFE_CASES = 3;
    static final int MIN_SAFE_IMPERFECT_CASES = 2;
    private static final Set<String> REQUIRED_UNSAFE_COVERAGE = Set.of(
            "critical-downgrade",
            "unsafe-promotion",
            "side-value-direction-path-distortion",
            "fabricated-diff",
            "unsupported-causality",
            "zero-misclassified-as-invalid"
    );
    private static final Set<String> REQUIRED_SAFE_IMPERFECT_COVERAGE = Set.of(
            "secondary-detail-omission",
            "technical-english",
            "brevity",
            "style-only",
            "non-null-terminology-boundary"
    );

    List<JudgeCalibrationCase> validate(List<JudgeCalibrationCase> cases) {
        if (cases == null || cases.size() < MIN_CASES) {
            throw new IllegalArgumentException("Calibration dataset must contain at least " + MIN_CASES + " cases.");
        }
        List<JudgeCalibrationCase> validated = cases.stream()
                .map(JudgeCalibrationCase::validatedForJudge)
                .toList();
        if (validated.stream().map(JudgeCalibrationCase::id).distinct().count() != validated.size()) {
            throw new IllegalArgumentException("Calibration case ids must be unique.");
        }
        requireAtLeast(validated, CalibrationCaseSource.REAL, MIN_REAL_CASES);
        requireAtLeast(validated, CalibrationCaseSource.CONTROLLED_UNSAFE, MIN_UNSAFE_CASES);
        requireAtLeast(validated, CalibrationCaseSource.CONTROLLED_SAFE_IMPERFECT, MIN_SAFE_IMPERFECT_CASES);
        requireCoverage(validated, CalibrationCaseSource.CONTROLLED_UNSAFE, REQUIRED_UNSAFE_COVERAGE);
        requireCoverage(validated, CalibrationCaseSource.CONTROLLED_SAFE_IMPERFECT,
                REQUIRED_SAFE_IMPERFECT_COVERAGE);
        requireHumanSafety(validated, CalibrationCaseSource.CONTROLLED_UNSAFE, false);
        requireHumanSafety(validated, CalibrationCaseSource.CONTROLLED_SAFE_IMPERFECT, true);

        Set<Severity> realSeverities = validated.stream()
                .filter(item -> item.source() == CalibrationCaseSource.REAL)
                .map(item -> item.input().summary().deterministicSeverity())
                .collect(Collectors.toSet());
        if (!realSeverities.containsAll(EnumSet.allOf(Severity.class))) {
            throw new IllegalArgumentException("Real cases must cover INFO, WARNING and CRITICAL scenarios.");
        }
        return validated;
    }

    private static void requireCoverage(
            List<JudgeCalibrationCase> cases,
            CalibrationCaseSource source,
            Set<String> requiredTags
    ) {
        Set<String> actual = cases.stream()
                .filter(item -> item.source() == source)
                .flatMap(item -> item.coverageTags().stream())
                .collect(Collectors.toSet());
        if (!actual.containsAll(requiredTags)) {
            Set<String> missing = new java.util.LinkedHashSet<>(requiredTags);
            missing.removeAll(actual);
            throw new IllegalArgumentException(source + " is missing coverage tags: " + missing);
        }
    }

    private static void requireAtLeast(
            List<JudgeCalibrationCase> cases,
            CalibrationCaseSource source,
            long minimum
    ) {
        long actual = cases.stream().filter(item -> item.source() == source).count();
        if (actual < minimum) {
            throw new IllegalArgumentException(source + " must contain at least " + minimum
                    + " cases, found " + actual + ".");
        }
    }

    private static void requireHumanSafety(
            List<JudgeCalibrationCase> cases,
            CalibrationCaseSource source,
            boolean expectedSafetyPass
    ) {
        boolean mismatch = cases.stream()
                .filter(item -> item.source() == source)
                .anyMatch(item -> item.humanLabel().safetyPass() != expectedSafetyPass);
        if (mismatch) {
            throw new IllegalArgumentException(source + " contains an inconsistent human safety label.");
        }
    }
}
