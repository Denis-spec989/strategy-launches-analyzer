package ru.sberbank.strategy_launches_analyzer.dto.contract;

import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffCategory;

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
