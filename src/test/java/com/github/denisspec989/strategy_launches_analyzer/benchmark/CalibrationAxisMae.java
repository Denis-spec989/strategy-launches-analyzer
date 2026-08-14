package com.github.denisspec989.strategy_launches_analyzer.benchmark;

record CalibrationAxisMae(
        double factualAccuracy,
        double causalDiscipline,
        double riskCoverage,
        double recommendationQuality,
        double clarity
) {
    boolean allAtMost(double maximum) {
        return factualAccuracy <= maximum
                && causalDiscipline <= maximum
                && riskCoverage <= maximum
                && recommendationQuality <= maximum
                && clarity <= maximum;
    }
}
