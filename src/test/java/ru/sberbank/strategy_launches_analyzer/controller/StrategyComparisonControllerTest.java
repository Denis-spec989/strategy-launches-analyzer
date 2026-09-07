package ru.sberbank.strategy_launches_analyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.sberbank.strategy_launches_analyzer.TestFixtures;
import ru.sberbank.strategy_launches_analyzer.dto.strategy.StrategyName;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;

@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Autowired)
class StrategyComparisonControllerTest {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Test
    void returnsDeterministicComparison() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("model-change")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyName").value("LGD_DIGITAL"))
                .andExpect(jsonPath("$.summary.strategyName").doesNotExist())
                .andExpect(jsonPath("$.summary.totalDiffs").value(3))
                .andExpect(jsonPath("$.diffs.length()").value(3))
                .andExpect(jsonPath("$.diffs[*].id", everyItem(matchesPattern(
                        "^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
                ))))
                .andExpect(jsonPath("$.metadata.requestId").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.metadata.mainStrategyVersion").value("main-v1"))
                .andExpect(jsonPath("$.metadata.shadowStrategyVersion").value("shadow-v2"))
                .andExpect(jsonPath("$.metadata.mainLaunchDt").value("2026-06-04T11:00:00Z"))
                .andExpect(jsonPath("$.metadata.shadowLaunchDt").value("2026-06-04T11:01:00Z"))
                .andExpect(jsonPath("$.metadata.attributes.environment").value("test"))
                .andExpect(jsonPath("$.analyzedAt").isString())
                .andExpect(jsonPath("$.diffs[0].description").doesNotExist());
    }

    @Test
    void oldLgdSpecificEndpointIsNotRegistered() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/lgd-digital/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("model-change")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Malformed JSON at line 1, column 2:")))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void rejectsMissingStrategy() throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        body.remove("strategy");

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("strategy is required."));
    }

    @Test
    void rejectsUnknownStrategy() throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        body.put("strategy", "lgd-digital");

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("strategy must be one of: LGD_DIGITAL."));
    }

    @Test
    void rejectsMissingStrategyResponse() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("strategy", StrategyName.LGD_DIGITAL.name());
        body.set("mainLaunch", objectMapper.createObjectNode());
        body.set("shadowLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/shadow.json"
        ));
        body.set("metadata", validMetadata());

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("mainLaunch.strategyResponse object is required."));
    }

    @Test
    void rejectsMissingMetadata() throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        body.remove("metadata");

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("metadata is required."));
    }

    @Test
    void rejectsMissingRequestId() throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        ((ObjectNode) body.path("metadata")).remove("requestId");

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("metadata.requestId is required."));
    }

    @Test
    void rejectsMissingEmptyAndBlankStrategyVersions() throws Exception {
        for (String fieldName : new String[]{"mainStrategyVersion", "shadowStrategyVersion"}) {
            ObjectNode missingBody = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
            ((ObjectNode) missingBody.path("metadata")).remove(fieldName);
            assertInvalidStrategyVersion(missingBody, fieldName);

            for (String invalidValue : new String[]{"", "  "}) {
                ObjectNode blankBody = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
                ((ObjectNode) blankBody.path("metadata")).put(fieldName, invalidValue);
                assertInvalidStrategyVersion(blankBody, fieldName);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-uuid",
            "11111111111111111111111111111111",
            "11111111-1111-1111-1111-AAAAAAAAAAAA"
    })
    void rejectsInvalidOrNonCanonicalRequestId(String requestId) throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        ((ObjectNode) body.path("metadata")).put("requestId", requestId);

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "metadata.requestId must be a canonical lowercase UUID, for example "
                                + "123e4567-e89b-12d3-a456-426614174000."
                ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mainLaunchDt", "shadowLaunchDt"})
    void rejectsMissingLaunchDates(String fieldName) throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        ((ObjectNode) body.path("metadata")).remove(fieldName);

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("metadata.%s is required.".formatted(fieldName)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"04.06.2026 11:01", "2026-06-04T11:01:00"})
    void rejectsMalformedOrTimezoneLessLaunchDate(String date) throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        ((ObjectNode) body.path("metadata")).put("shadowLaunchDt", date);

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "metadata.shadowLaunchDt must be an ISO-8601 date-time with timezone, for example "
                                + "2026-08-21T10:43:25Z."
                ));
    }

    @Test
    void reportsAllMissingMetadataDatesAfterRequestIdIsValid() throws Exception {
        ObjectNode body = objectMapper.readValue(requestBody("model-change"), ObjectNode.class);
        ObjectNode metadata = (ObjectNode) body.path("metadata");
        metadata.remove("mainLaunchDt");
        metadata.remove("shadowLaunchDt");

        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "metadata.mainLaunchDt is required.; metadata.shadowLaunchDt is required."
                ));
    }

    private String requestBody(String fixtureName) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("strategy", StrategyName.LGD_DIGITAL.name());
        body.set("mainLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/%s/main.json".formatted(fixtureName)
        ));
        body.set("shadowLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/%s/shadow.json".formatted(fixtureName)
        ));
        body.set("metadata", validMetadata());
        return objectMapper.writeValueAsString(body);
    }

    private void assertInvalidStrategyVersion(ObjectNode body, String fieldName) throws Exception {
        mockMvc.perform(post("/api/v1/strategies/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("metadata.%s is required.".formatted(fieldName)));
    }

    private ObjectNode validMetadata() {
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("requestId", "11111111-1111-1111-1111-111111111111")
                .put("mainLaunchId", "MAIN-1")
                .put("shadowLaunchId", "SHADOW-1")
                .put("mainStrategyVersion", "main-v1")
                .put("shadowStrategyVersion", "shadow-v2")
                .put("mainLaunchDt", "2026-06-04T14:00:00+03:00")
                .put("shadowLaunchDt", "2026-06-04T14:01:00+03:00");
        metadata.set("attributes", objectMapper.createObjectNode().put("environment", "test"));
        return metadata;
    }

}
