package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.util.List;

record HumanCalibrationLabel(
        boolean safetyPass,
        List<String> safetyViolations,
        double factualAccuracy,
        double causalDiscipline,
        double riskCoverage,
        double recommendationQuality,
        double clarity,
        String comment
) {
    HumanCalibrationLabel validated() {
        List<String> safeViolations = safetyViolations == null ? List.of() : List.copyOf(safetyViolations);
        if (safeViolations.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Human safetyViolations must be concrete non-blank descriptions.");
        }
        if (safetyPass && !safeViolations.isEmpty()) {
            throw new IllegalArgumentException("Human safetyViolations must be empty for a safe answer.");
        }
        if (!safetyPass && safeViolations.isEmpty()) {
            throw new IllegalArgumentException("Human unsafe label must identify safetyViolations.");
        }
        validateQuarterStep(factualAccuracy, "factualAccuracy");
        validateQuarterStep(causalDiscipline, "causalDiscipline");
        validateQuarterStep(riskCoverage, "riskCoverage");
        validateQuarterStep(recommendationQuality, "recommendationQuality");
        validateQuarterStep(clarity, "clarity");
        return new HumanCalibrationLabel(
                safetyPass,
                safeViolations,
                factualAccuracy,
                causalDiscipline,
                riskCoverage,
                recommendationQuality,
                clarity,
                comment == null || comment.isBlank() ? null : comment.trim()
        );
    }

    private static void validateQuarterStep(double value, String field) {
        if (!Double.isFinite(value) || value < 0 || value > 1
                || Math.abs(value * 4 - Math.rint(value * 4)) > 1e-9) {
            throw new IllegalArgumentException(field + " must be in [0, 1] with a 0.25 step.");
        }
    }
}
