package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkConfigurationTest {

    @Test
    void usesTwoDefaultCandidatesAndFixedJudge() {
        String previousModels = System.clearProperty("benchmark.models");
        String previousJudge = System.clearProperty("benchmark.judge-model");
        try {
            BenchmarkConfiguration configuration = BenchmarkConfiguration.fromSystemProperties();

            assertThat(configuration.models()).containsExactly("gpt-5.4", "gpt-5.5");
            assertThat(configuration.judgeModel()).isEqualTo("gpt-5.6-sol");
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
