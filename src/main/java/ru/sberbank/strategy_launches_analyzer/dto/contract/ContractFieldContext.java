package ru.sberbank.strategy_launches_analyzer.dto.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffCategory;

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
