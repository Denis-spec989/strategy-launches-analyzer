package com.github.denisspec989.strategy_launches_analyzer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "strategy-launches-analyzer.agent.gigachat.auth-mode=user-password",
        "strategy-launches-analyzer.agent.gigachat.model=test-model",
        "strategy-launches-analyzer.agent.gigachat.user-password.api-url=https://api.example/v1",
        "strategy-launches-analyzer.agent.gigachat.user-password.auth-api-url=https://auth.example/v1",
        "strategy-launches-analyzer.agent.gigachat.user-password.username=test-user",
        "strategy-launches-analyzer.agent.gigachat.user-password.password=test-password",
        "strategy-launches-analyzer.agent.gigachat.user-password.scope=GIGACHAT_API_PERS"
})
@AutoConfigureMockMvc
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Autowired)
class PublicOpenApiContractTest {
    private static final Path COMMITTED_SPEC = Path.of(
            "docs", "openapi", "strategy-comparison-v1.openapi.yaml"
    );

    private final MockMvc mockMvc;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void committedSpecificationMatchesCodeFirstContract() throws Exception {
        String generatedYaml = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode generated = yamlMapper.readTree(generatedYaml);
        JsonNode committed = yamlMapper.readTree(Files.readString(COMMITTED_SPEC));

        assertThat(generated).isEqualTo(committed);
    }

    @Test
    void specificationLocksV1ContractAndRemovedFields() throws Exception {
        JsonNode specification = yamlMapper.readTree(Files.readString(COMMITTED_SPEC));
        JsonNode schemas = specification.path("components").path("schemas");
        JsonNode metadata = schemas.path("LaunchMetadata");
        JsonNode agentAnalysis = schemas.path("AgentAnalysis");
        JsonNode completedAnalysis = schemas.path("CompletedAgentAnalysis");
        JsonNode failedAnalysis = schemas.path("FailedAgentAnalysis");
        JsonNode diff = schemas.path("DiffEntry").path("properties");
        JsonNode diffExplanation = schemas.path("DiffExplanation").path("properties");
        JsonNode summary = schemas.path("ComparisonSummary").path("properties");
        JsonNode errorResponse = schemas.path("ErrorResponse");
        JsonNode contractIssue = schemas.path("ContractIssue");

        assertThat(specification.path("info").path("version").asText()).isEqualTo("1.0.0");
        assertThat(specification.path("paths").fieldNames()).toIterable()
                .containsExactly("/api/v1/strategies/compare");
        assertThat(metadata.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("requestId", "mainLaunchDt", "shadowLaunchDt");
        assertThat(metadata.path("properties").path("requestId").path("format").asText()).isEqualTo("uuid");
        assertThat(metadata.path("properties").path("requestId").has("default")).isFalse();
        assertThat(metadata.path("properties").path("mainLaunchDt").path("format").asText())
                .isEqualTo("date-time");
        assertThat(schemas.path("CompareStrategyResponse").path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "strategyName",
                        "contractVersion",
                        "analyzedAt",
                        "metadata",
                        "summary",
                        "diffs",
                        "contractValidation",
                        "agentAnalysis"
                );
        assertThat(agentAnalysis.path("oneOf")).hasSize(2);
        assertThat(agentAnalysis.path("discriminator").path("propertyName").asText()).isEqualTo("status");
        assertThat(completedAnalysis.path("properties").has("failureReason")).isFalse();
        assertThat(completedAnalysis.path("properties").has("errorMessage")).isFalse();
        assertThat(completedAnalysis.path("required")).extracting(JsonNode::asText)
                .contains("status", "overallSeverity", "summary", "businessImpact", "technicalRisks",
                        "recommendations", "diffExplanations");
        assertThat(failedAnalysis.path("properties").fieldNames()).toIterable()
                .containsExactly("status", "failureReason", "errorMessage");
        assertThat(failedAnalysis.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("status", "failureReason", "errorMessage");
        assertThat(specification.toString()).doesNotContain("tokenUsage", "responseSchemaVersion", "diagnostics");
        assertThat(summary.has("strategyName")).isFalse();
        assertThat(schemas.path("ComparisonSummary").path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "totalDiffs",
                        "metricDiffs",
                        "modelDiffs",
                        "calculationContextDiffs",
                        "contractTechnicalDiffs",
                        "contractValidationIssues",
                        "hasCriticalIssues",
                        "deterministicSeverity"
                );
        assertThat(diff.path("id").path("format").asText()).isEqualTo("uuid");
        assertThat(schemas.path("DiffEntry").path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("id", "path", "type", "category", "deterministicSeverity");
        assertThat(diff.path("deterministicSeverity").path("enum")).extracting(JsonNode::asText)
                .containsExactly("WARNING", "CRITICAL");
        assertThat(diffExplanation.path("diffId").path("format").asText()).isEqualTo("uuid");
        assertThat(contractIssue.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("id", "side", "path", "type", "severity", "expected", "actual", "message");
        assertThat(errorResponse.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("timestamp", "status", "error", "message");
        assertThat(diff.has("description")).isFalse();
        assertThat(diff.has("field")).isFalse();
        assertThat(diff.has("relatedContractIssueIds")).isFalse();
        assertThat(agentAnalysis.path("properties").isMissingNode()).isTrue();
        assertThat(specification.toString()).doesNotContain("\"default\"");
    }
}
