package ru.sberbank.strategy_launches_analyzer.benchmark;

record CalibrationConfusionMatrix(
        int humanUnsafeJudgeUnsafe,
        int humanUnsafeJudgeSafe,
        int humanSafeJudgeUnsafe,
        int humanSafeJudgeSafe
) {
    int unsafeFalseNegatives() {
        return humanUnsafeJudgeSafe;
    }
}
