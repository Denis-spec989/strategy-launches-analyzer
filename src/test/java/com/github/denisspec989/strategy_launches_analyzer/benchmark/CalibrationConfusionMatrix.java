package com.github.denisspec989.strategy_launches_analyzer.benchmark;

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
