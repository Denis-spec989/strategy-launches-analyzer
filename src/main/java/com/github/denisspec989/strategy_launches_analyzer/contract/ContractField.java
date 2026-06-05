package com.github.denisspec989.strategy_launches_analyzer.contract;

import com.github.denisspec989.strategy_launches_analyzer.domain.DiffCategory;

public record ContractField(
        String path,
        ContractValueType valueType,
        String format,
        String cardinality,
        boolean nullable,
        DiffCategory category,
        String description,
        String unit,
        String summaryGuidance
) {
    public boolean required() {
        return "1..1".equals(cardinality);
    }

    public boolean isLeaf() {
        return valueType != ContractValueType.OBJECT;
    }
}
