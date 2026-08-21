package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.TreeMap;

public final class StructuredOutputSchema {
    private StructuredOutputSchema() {
    }

    public static JsonNode create(ObjectMapper objectMapper, Class<?> responseType) {
        String schema = new BeanOutputConverter<>(responseType).getJsonSchema();
        try {
            return objectMapper.readTree(schema);
        } catch (JsonProcessingException ex) {
            throw new AgentAnalysisException("Failed to build the structured output schema.", ex);
        }
    }

    public static String canonical(ObjectMapper objectMapper, Class<?> responseType) {
        try {
            return objectMapper.writeValueAsString(sort(create(objectMapper, responseType), objectMapper));
        } catch (JsonProcessingException ex) {
            throw new AgentAnalysisException("Failed to canonicalize the structured output schema.", ex);
        }
    }

    private static JsonNode sort(JsonNode node, ObjectMapper objectMapper) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> sorted.set(name, sort(value, objectMapper)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(sort(value, objectMapper)));
            return sorted;
        }
        return node;
    }
}
