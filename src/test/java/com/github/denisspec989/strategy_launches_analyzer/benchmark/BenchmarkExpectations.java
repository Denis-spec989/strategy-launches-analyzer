package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.util.List;

record BenchmarkExpectations(
        String id,
        List<String> tags,
        SemanticExpectations semantic
) {
    BenchmarkExpectations {
        tags = tags == null ? List.of() : List.copyOf(tags);
        semantic = semantic == null ? new SemanticExpectations(null, null, null) : semantic;
    }
}
