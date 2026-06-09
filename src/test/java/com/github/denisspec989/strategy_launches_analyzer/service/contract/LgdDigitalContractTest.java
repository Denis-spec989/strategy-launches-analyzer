package com.github.denisspec989.strategy_launches_analyzer.service.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractField;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractValueType;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffCategory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LgdDigitalContractTest {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void openApiContractIsStandardDocumentWithLgdDigitalSchema() throws Exception {
        JsonNode openApi = openApi();

        assertThat(openApi.path("openapi").asText()).isEqualTo("3.0.3");
        assertThat(openApi.path("info").path("x-strategy-name").asText()).isEqualTo(LgdDigitalContract.STRATEGY_NAME);
        assertThat(openApi.path("paths").isObject()).isTrue();
        assertThat(openApi.path("components").path("schemas").has(LgdDigitalContract.OPENAPI_SCHEMA)).isTrue();
        assertObjectSchemasDisallowAdditionalProperties(
                openApi.path("components").path("schemas").path(LgdDigitalContract.OPENAPI_SCHEMA)
        );
    }

    @Test
    void loadsLgdDigitalContractFromOpenApiInContractOrder() {
        LgdDigitalContract contract = new LgdDigitalContract();

        assertThat(contract.version()).isEqualTo("v1");
        assertThat(contract.fields())
                .extracting(ContractField::path)
                .containsExactly(
                        "strategyResponse",
                        "strategyResponse.lgdData",
                        "strategyResponse.lgdData.lgd",
                        "strategyResponse.lgdData.lgdModel",
                        "strategyResponse.lgdData.lgdDt",
                        "strategyResponse.calculationInfo",
                        "strategyResponse.calculationInfo.mode",
                        "strategyResponse.calculationInfo.type",
                        "strategyResponse.calculationInfo.scenario",
                        "strategyResponse.calculationInfo.usedDefaultValue",
                        "strategyResponse.calculationInfo.defaultValueReason"
                );
    }

    @Test
    void mapsOpenApiTypesRequiredNullableAndExtensionsToContractFields() {
        LgdDigitalContract contract = new LgdDigitalContract();

        assertThat(contract.field("strategyResponse.lgdData.lgd"))
                .get()
                .satisfies(field -> {
                    assertThat(field.valueType()).isEqualTo(ContractValueType.NUMBER);
                    assertThat(field.format()).isEqualTo("double");
                    assertThat(field.cardinality()).isEqualTo("1..1");
                    assertThat(field.required()).isTrue();
                    assertThat(field.nullable()).isFalse();
                    assertThat(field.category()).isEqualTo(DiffCategory.METRIC);
                    assertThat(field.description()).isEqualTo("LGD-\u043F\u043E\u0442\u0435\u0440\u0438 \u043F\u0440\u0438 \u0434\u0435\u0444\u043E\u043B\u0442\u0435 (%)");
                    assertThat(field.unit()).isEqualTo("percent");
                    assertThat(field.summaryGuidance()).contains("LGD-\u043F\u043E\u0442\u0435\u0440\u044C");
                });
        assertThat(contract.field("strategyResponse.lgdData.lgdModel"))
                .get()
                .satisfies(field -> {
                    assertThat(field.valueType()).isEqualTo(ContractValueType.STRING);
                    assertThat(field.format()).isNull();
                    assertThat(field.cardinality()).isEqualTo("1..1");
                    assertThat(field.required()).isTrue();
                    assertThat(field.nullable()).isTrue();
                    assertThat(field.category()).isEqualTo(DiffCategory.MODEL);
                    assertThat(field.description()).isEqualTo("\u041C\u043E\u0434\u0435\u043B\u044C \u0440\u0430\u0441\u0447\u0435\u0442\u0430");
                });
        assertThat(contract.field("strategyResponse.calculationInfo.usingCollateral")).isEmpty();
    }

    private JsonNode openApi() throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(LgdDigitalContract.OPENAPI_RESOURCE)) {
            assertThat(stream).isNotNull();
            return yamlMapper.readTree(stream);
        }
    }

    private static void assertObjectSchemasDisallowAdditionalProperties(JsonNode schema) {
        if ("object".equals(schema.path("type").asText())) {
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            JsonNode properties = schema.path("properties");
            if (properties.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> propertyIterator = properties.properties().iterator();
                while (propertyIterator.hasNext()) {
                    assertObjectSchemasDisallowAdditionalProperties(propertyIterator.next().getValue());
                }
            }
        }
    }
}
