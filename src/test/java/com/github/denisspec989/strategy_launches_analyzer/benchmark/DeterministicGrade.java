package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.util.List;

record DeterministicGrade(
        boolean passed,
        List<String> violations,
        int expectedDiffs,
        int coveredDiffs,
        double cyrillicRatio
) {
    DeterministicGrade {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
