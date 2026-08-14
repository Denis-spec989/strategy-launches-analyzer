package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenchmarkConfigurationTest {

    @Test
    void requiresExplicitCandidateAndJudgeModels() {
        String previousModels = System.clearProperty("benchmark.models");
        String previousJudge = System.clearProperty("benchmark.judge-model");
        try {
            assertThatThrownBy(BenchmarkConfiguration::fromSystemProperties)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("benchmark.models is required.");
        } finally {
            restore("benchmark.models", previousModels);
            restore("benchmark.judge-model", previousJudge);
        }
    }

    @Test
    void usesExplicitCandidateAndJudgeModels() {
        String previousModels = System.setProperty("benchmark.models", "GigaChat-2-Pro,GigaChat-2-Max");
        String previousJudge = System.setProperty("benchmark.judge-model", "GigaChat-2-Max");
        try {
            BenchmarkConfiguration configuration = BenchmarkConfiguration.fromSystemProperties();

            assertThat(configuration.models()).containsExactly("GigaChat-2-Pro", "GigaChat-2-Max");
            assertThat(configuration.judgeModel()).isEqualTo("GigaChat-2-Max");
        } finally {
            restore("benchmark.models", previousModels);
            restore("benchmark.judge-model", previousJudge);
        }
    }

    @Test
    void requiresExplicitJudgeModel() {
        String previousModels = System.setProperty("benchmark.models", "GigaChat-2-Pro,GigaChat-2-Max");
        String previousJudge = System.clearProperty("benchmark.judge-model");
        try {
            assertThatThrownBy(BenchmarkConfiguration::fromSystemProperties)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("benchmark.judge-model is required.");
        } finally {
            restore("benchmark.models", previousModels);
            restore("benchmark.judge-model", previousJudge);
        }
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}
