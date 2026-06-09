package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

public enum DiffType {
    NUMERIC_VALUE_CHANGED,
    STRING_VALUE_CHANGED,
    BOOLEAN_VALUE_CHANGED,
    FIELD_ADDED_IN_SHADOW,
    FIELD_MISSING_IN_SHADOW,
    FIELD_ADDED_IN_MAIN,
    TYPE_MISMATCH,
    NULLABILITY_VIOLATION,
    REQUIRED_FIELD_MISSING
}
