package com.github.denisspec989.strategy_launches_analyzer.contract;

import com.github.denisspec989.strategy_launches_analyzer.domain.DiffCategory;

public record ContractField(
        String path,
        ContractValueType valueType,
        boolean required,
        boolean nullable,
        DiffCategory category
) {
    public boolean isLeaf() {
        return valueType != ContractValueType.OBJECT;
    }
}
