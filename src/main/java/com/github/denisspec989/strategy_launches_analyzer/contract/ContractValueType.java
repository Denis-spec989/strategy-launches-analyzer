package com.github.denisspec989.strategy_launches_analyzer.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public enum ContractValueType {
    OBJECT,
    NUMBER,
    STRING,
    BOOLEAN;

    public boolean matches(JsonNode node) {
        return switch (this) {
            case OBJECT -> node.isObject();
            case NUMBER -> node.isNumber();
            case STRING -> node.isTextual();
            case BOOLEAN -> node.isBoolean();
        };
    }

    public static String actualTypeOf(JsonNode node) {
        if (node == null || node instanceof MissingNode || node.isMissingNode()) {
            return "missing";
        }
        if (node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        return node.getNodeType().name().toLowerCase();
    }
}
