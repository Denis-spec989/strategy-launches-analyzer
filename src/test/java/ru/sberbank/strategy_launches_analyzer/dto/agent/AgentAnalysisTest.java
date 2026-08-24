package ru.sberbank.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAnalysisTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest
    @EnumSource(AgentFallbackReason.class)
    void failedAnalysisExposesOnlySafeFailureFields(AgentFallbackReason reason) {
        JsonNode json = objectMapper.valueToTree(AgentAnalysis.failed(reason));

        assertThat(json.size()).isEqualTo(3);
        assertThat(json.path("status").asText()).isEqualTo("FAILED");
        assertThat(json.path("failureReason").asText()).isEqualTo(reason.name());
        assertThat(json.path("errorMessage").asText()).isEqualTo(reason.publicMessage());
        assertThat(json.has("overallSeverity")).isFalse();
        assertThat(json.has("summary")).isFalse();
        assertThat(json.has("businessImpact")).isFalse();
        assertThat(json.has("technicalRisks")).isFalse();
        assertThat(json.has("recommendations")).isFalse();
        assertThat(json.has("diffExplanations")).isFalse();
        assertThat(json.has("tokenUsage")).isFalse();
        assertThat(json.has("model")).isFalse();
        assertThat(reason.publicMessage())
                .doesNotContain("Exception", "http://", "https://", "java.");
    }
}
