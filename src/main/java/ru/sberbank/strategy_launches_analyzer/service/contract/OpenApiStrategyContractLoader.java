package ru.sberbank.strategy_launches_analyzer.service.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractField;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractValueType;
import ru.sberbank.strategy_launches_analyzer.dto.contract.StrategyContractDefinition;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffCategory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class OpenApiStrategyContractLoader {
    private static final String OPENAPI_VERSION = "3.0.3";
    private static final String EXT_STRATEGY_NAME = "x-strategy-name";
    private static final String EXT_DIFF_CATEGORY = "x-diff-category";
    private static final String EXT_UNIT = "x-unit";
    private static final String EXT_SUMMARY_GUIDANCE = "x-summary-guidance";

    private final ObjectMapper objectMapper;

    public OpenApiStrategyContractLoader() {
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    public StrategyContractDefinition load(String resourcePath, String schemaName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("OpenAPI contract resource not found: " + resourcePath);
            }
            return load(stream, schemaName);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load OpenAPI contract from " + resourcePath, ex);
        }
    }

    StrategyContractDefinition load(InputStream stream, String schemaName) throws IOException {
        JsonNode openApi = objectMapper.readTree(stream);
        assertText(openApi, "openapi", OPENAPI_VERSION, "openapi");

        JsonNode info = requiredObject(openApi.path("info"), "info");
        String strategyName = requiredText(info, EXT_STRATEGY_NAME, "info." + EXT_STRATEGY_NAME);
        String version = requiredText(info, "version", "info.version");

        JsonNode schemas = requiredObject(openApi.path("components").path("schemas"), "components.schemas");
        JsonNode schema = requiredObject(schemas.path(schemaName), "components.schemas." + schemaName);
        assertType(schema, "object", schemaName);
        assertAdditionalPropertiesFalse(schema, schemaName);

        List<ContractField> fields = new ArrayList<>();
        Set<String> rootRequired = requiredNames(schema, schemaName);
        JsonNode properties = requiredObject(schema.path("properties"), schemaName + ".properties");
        Iterator<Map.Entry<String, JsonNode>> propertyIterator = properties.properties().iterator();
        while (propertyIterator.hasNext()) {
            Map.Entry<String, JsonNode> property = propertyIterator.next();
            collectField(property.getKey(), property.getValue(), rootRequired, property.getKey(), fields);
        }
        if (fields.isEmpty()) {
            throw new IllegalStateException("OpenAPI schema has no contract fields: " + schemaName);
        }

        return new StrategyContractDefinition(
                strategyName,
                version,
                fields.get(0).path(),
                List.copyOf(fields)
        );
    }

    private static void collectField(
            String path,
            JsonNode schema,
            Set<String> parentRequired,
            String propertyName,
            List<ContractField> fields
    ) {
        JsonNode objectSchema = requiredObject(schema, path);
        String type = requiredText(objectSchema, "type", path + ".type");
        ContractValueType valueType = valueType(type, path);
        fields.add(new ContractField(
                path,
                valueType,
                optionalText(objectSchema, "format"),
                parentRequired.contains(propertyName) ? "1..1" : "0..1",
                objectSchema.path("nullable").asBoolean(false),
                DiffCategory.valueOf(requiredText(objectSchema, EXT_DIFF_CATEGORY, path + "." + EXT_DIFF_CATEGORY)),
                optionalText(objectSchema, "description"),
                optionalText(objectSchema, EXT_UNIT),
                optionalText(objectSchema, EXT_SUMMARY_GUIDANCE)
        ));

        if (valueType != ContractValueType.OBJECT) {
            return;
        }

        assertAdditionalPropertiesFalse(objectSchema, path);

        JsonNode properties = requiredObject(objectSchema.path("properties"), path + ".properties");
        Set<String> requiredNames = requiredNames(objectSchema, path);
        Iterator<Map.Entry<String, JsonNode>> propertyIterator = properties.properties().iterator();
        while (propertyIterator.hasNext()) {
            Map.Entry<String, JsonNode> child = propertyIterator.next();
            collectField(path + "." + child.getKey(), child.getValue(), requiredNames, child.getKey(), fields);
        }
    }

    private static Set<String> requiredNames(JsonNode schema, String path) {
        JsonNode required = schema.path("required");
        if (required.isMissingNode()) {
            return Set.of();
        }
        if (!required.isArray()) {
            throw new IllegalStateException(path + ".required must be an array.");
        }
        return StreamSupport.stream(required.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ContractValueType valueType(String openApiType, String path) {
        return switch (openApiType.toLowerCase(Locale.ROOT)) {
            case "object" -> ContractValueType.OBJECT;
            case "number", "integer" -> ContractValueType.NUMBER;
            case "string" -> ContractValueType.STRING;
            case "boolean" -> ContractValueType.BOOLEAN;
            default -> throw new IllegalStateException("Unsupported OpenAPI type at " + path + ": " + openApiType);
        };
    }

    private static JsonNode requiredObject(JsonNode node, String path) {
        if (!node.isObject()) {
            throw new IllegalStateException(path + " must be an object.");
        }
        return node;
    }

    private static void assertType(JsonNode node, String expectedType, String path) {
        assertText(node, "type", expectedType, path + ".type");
    }

    private static void assertAdditionalPropertiesFalse(JsonNode node, String path) {
        JsonNode additionalProperties = node.path("additionalProperties");
        if (!additionalProperties.isBoolean() || additionalProperties.booleanValue()) {
            throw new IllegalStateException(path + ".additionalProperties must be false.");
        }
    }

    private static void assertText(JsonNode node, String fieldName, String expectedValue, String path) {
        String actualValue = requiredText(node, fieldName, path);
        if (!expectedValue.equals(actualValue)) {
            throw new IllegalStateException(path + " must be " + expectedValue + ", actual: " + actualValue);
        }
    }

    private static String requiredText(JsonNode node, String fieldName, String path) {
        String value = optionalText(node, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(path + " is required.");
        }
        return value;
    }

    private static String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
