package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.util.List;

record DeterministicGrade(
        boolean passed,
        boolean languageCompliant,
        List<String> violations,
        List<String> languageViolations,
        int expectedDiffs,
        int coveredDiffs
) {
    DeterministicGrade {
        violations = violations == null ? List.of() : List.copyOf(violations);
        languageViolations = languageViolations == null ? List.of() : List.copyOf(languageViolations);
    }

    boolean rawCompliant() {
        return passed && languageCompliant;
    }
}
