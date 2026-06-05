package com.github.denisspec989.strategy_launches_analyzer.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.denisspec989.strategy_launches_analyzer.contract.ContractField;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffCategory;

import java.util.Locale;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractFieldContext(
        String path,
        String type,
        String format,
        String cardinality,
        boolean nullable,
        DiffCategory category,
        String description,
        String unit,
        String summaryGuidance
) {
    public static ContractFieldContext from(ContractField field) {
        return new ContractFieldContext(
                field.path(),
                field.valueType().name().toLowerCase(Locale.ROOT),
                field.format(),
                field.cardinality(),
                field.nullable(),
                field.category(),
                field.description(),
                field.unit(),
                field.summaryGuidance()
        );
    }
}
