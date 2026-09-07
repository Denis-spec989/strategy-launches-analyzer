package ru.sberbank.strategy_launches_analyzer.controller;

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

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
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
        JsonNode diff = schemas.path("DiffEntry").path("properties");
        JsonNode summary = schemas.path("ComparisonSummary").path("properties");
        JsonNode errorResponse = schemas.path("ErrorResponse");
        JsonNode contractIssue = schemas.path("ContractIssue");

        assertThat(specification.path("info").path("version").asText()).isEqualTo("1.1.0");
        assertThat(specification.path("paths").fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "/api/v1/strategies/compare",
                        "/api/v1/strategies/compare/batch"
                );
        JsonNode batchPost = specification.path("paths")
                .path("/api/v1/strategies/compare/batch")
                .path("post");
        assertThat(batchPost.path("requestBody").path("content").has("application/x-ndjson")).isTrue();
        assertThat(batchPost.path("responses").path("200").path("content")
                .path("application/zip").path("schema").path("format").asText()).isEqualTo("binary");
        JsonNode batchResponseHeaders = batchPost.path("responses").path("200").path("headers");
        assertThat(batchResponseHeaders.path("X-Batch-Id").path("schema").path("format").asText())
                .isEqualTo("uuid");
        assertThat(batchResponseHeaders.has("Content-Disposition")).isTrue();
        assertThat(batchPost.path("responses").path("400").path("content").has("application/json"))
                .isTrue();
        assertThat(batchPost.path("responses").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("200", "400", "413", "429", "500", "503", "507");
        assertThat(metadata.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "requestId",
                        "mainStrategyVersion",
                        "shadowStrategyVersion",
                        "mainLaunchDt",
                        "shadowLaunchDt"
                );
        assertThat(metadata.path("properties").path("requestId").path("format").asText()).isEqualTo("uuid");
        assertThat(metadata.path("properties").path("requestId").has("default")).isFalse();
        assertThat(metadata.path("properties").path("mainStrategyVersion").path("type").asText())
                .isEqualTo("string");
        assertThat(metadata.path("properties").path("mainStrategyVersion").path("minLength").asInt())
                .isEqualTo(1);
        assertThat(metadata.path("properties").path("shadowStrategyVersion").path("type").asText())
                .isEqualTo("string");
        assertThat(metadata.path("properties").path("shadowStrategyVersion").path("minLength").asInt())
                .isEqualTo(1);
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
                        "contractValidation"
                );
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
        assertThat(contractIssue.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("id", "side", "path", "type", "severity", "expected", "actual", "message");
        assertThat(errorResponse.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("timestamp", "status", "error", "message");
        assertThat(diff.has("description")).isFalse();
        assertThat(diff.has("field")).isFalse();
        assertThat(diff.has("relatedContractIssueIds")).isFalse();
        assertThat(specification.toString()).doesNotContain("\"default\"");
    }
}
