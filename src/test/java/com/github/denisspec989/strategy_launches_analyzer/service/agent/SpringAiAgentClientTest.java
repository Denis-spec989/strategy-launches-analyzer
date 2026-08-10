package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiAgentClientTest {
    @Test
    void requestOptionsOverrideModelForOneCall() {
        assertThat(SpringAiAgentClient.requestOptions("benchmark-model").build().getModel())
                .isEqualTo("benchmark-model");
    }
}
