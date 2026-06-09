package com.github.denisspec989.strategy_launches_analyzer.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonValueSummary(
        String jsonType,
        String preview,
        boolean truncated,
        Integer objectFieldCount,
        Integer arrayElementCount,
        Integer stringLength
) {
    private static final int MAX_PREVIEW_LENGTH = 120;
    private static final int MAX_PREVIEW_ITEMS = 5;

    public static JsonValueSummary from(JsonNode value) {
        if (value == null || value.isMissingNode()) {
            return new JsonValueSummary("missing", "missing", false, null, null, null);
        }
        if (value.isNull()) {
            return new JsonValueSummary("null", "null", false, null, null, null);
        }
        if (value.isObject()) {
            return objectSummary(value);
        }
        if (value.isArray()) {
            return arraySummary(value);
        }
        if (value.isTextual()) {
            String text = sanitizeText(value.asText());
            String preview = limit(text, MAX_PREVIEW_LENGTH);
            return new JsonValueSummary(
                    "string",
                    preview,
                    preview.length() < text.length(),
                    null,
                    null,
                    text.length()
            );
        }
        if (value.isNumber()) {
            return new JsonValueSummary("number", value.asText(), false, null, null, null);
        }
        if (value.isBoolean()) {
            return new JsonValueSummary("boolean", value.asText(), false, null, null, null);
        }
        String preview = limit(sanitizeText(value.toString()), MAX_PREVIEW_LENGTH);
        return new JsonValueSummary(jsonType(value), preview, preview.length() < value.toString().length(), null, null, null);
    }

    private static JsonValueSummary objectSummary(JsonNode value) {
        List<String> fieldNames = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, JsonNode> ignored : iterable(value.properties())) {
            count++;
            if (fieldNames.size() < MAX_PREVIEW_ITEMS) {
                fieldNames.add(sanitizeText(ignored.getKey()));
            }
        }
        boolean truncated = count > fieldNames.size();
        String preview = fieldNames.isEmpty()
                ? "fields=[]"
                : "fields=[" + String.join(", ", fieldNames) + (truncated ? ", ..." : "") + "]";
        return new JsonValueSummary("object", preview, truncated, count, null, null);
    }

    private static JsonValueSummary arraySummary(JsonNode value) {
        List<String> elementTypes = new ArrayList<>();
        int count = 0;
        for (JsonNode element : value) {
            count++;
            if (elementTypes.size() < MAX_PREVIEW_ITEMS) {
                elementTypes.add(jsonType(element));
            }
        }
        boolean truncated = count > elementTypes.size();
        String preview = elementTypes.isEmpty()
                ? "elementTypes=[]"
                : "elementTypes=[" + String.join(", ", elementTypes) + (truncated ? ", ..." : "") + "]";
        return new JsonValueSummary("array", preview, truncated, null, count, null);
    }

    private static Iterable<Map.Entry<String, JsonNode>> iterable(Iterable<Map.Entry<String, JsonNode>> iterable) {
        return iterable;
    }

    private static String jsonType(JsonNode value) {
        if (value == null || value.isMissingNode()) {
            return "missing";
        }
        if (value.isNull()) {
            return "null";
        }
        if (value.isObject()) {
            return "object";
        }
        if (value.isArray()) {
            return "array";
        }
        if (value.isTextual()) {
            return "string";
        }
        if (value.isNumber()) {
            return "number";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        return value.getNodeType().name().toLowerCase();
    }

    private static String limit(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String sanitizeText(String value) {
        return value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
    }
}
