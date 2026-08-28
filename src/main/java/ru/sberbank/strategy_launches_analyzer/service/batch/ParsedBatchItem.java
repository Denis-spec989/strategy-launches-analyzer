package ru.sberbank.strategy_launches_analyzer.service.batch;

import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemResult;

public record ParsedBatchItem(
        BatchInputLine inputLine,
        CompareStrategyRequest request,
        BatchItemResult failure
) {
    public boolean valid() {
        return request != null;
    }
}
