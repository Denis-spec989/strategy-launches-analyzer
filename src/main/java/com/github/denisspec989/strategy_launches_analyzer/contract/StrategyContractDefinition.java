package com.github.denisspec989.strategy_launches_analyzer.contract;

import java.util.List;

public record StrategyContractDefinition(
        String strategyName,
        String version,
        String rootPath,
        List<ContractField> fields
) {
}
