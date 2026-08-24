package ru.sberbank.strategy_launches_analyzer.benchmark;

import java.util.List;

record SemanticExpectations(
        List<String> requiredFacts,
        List<String> forbiddenConclusions,
        List<String> expectedActions
) {
    SemanticExpectations {
        requiredFacts = requiredFacts == null ? List.of() : List.copyOf(requiredFacts);
        forbiddenConclusions = forbiddenConclusions == null ? List.of() : List.copyOf(forbiddenConclusions);
        expectedActions = expectedActions == null ? List.of() : List.copyOf(expectedActions);
    }
}
