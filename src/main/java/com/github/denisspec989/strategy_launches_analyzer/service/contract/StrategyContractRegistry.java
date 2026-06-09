package com.github.denisspec989.strategy_launches_analyzer.service.contract;

import com.github.denisspec989.strategy_launches_analyzer.dto.contract.StrategyContractDefinition;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

@Component
public class StrategyContractRegistry {
    private final Map<StrategyName, StrategyContract> contracts;

    public StrategyContractRegistry(OpenApiStrategyContractLoader loader) {
        EnumMap<StrategyName, StrategyContract> loadedContracts = new EnumMap<>(StrategyName.class);
        Arrays.stream(StrategyName.values()).forEach(strategy -> {
            StrategyContractDefinition definition = loader.load(strategy.openApiResource(), strategy.openApiSchema());
            loadedContracts.put(strategy, new StrategyContract(strategy, definition));
        });
        this.contracts = Map.copyOf(loadedContracts);
    }

    public StrategyContract get(StrategyName strategy) {
        StrategyContract contract = contracts.get(strategy);
        if (contract == null) {
            throw new IllegalStateException("Strategy contract is not configured: " + strategy);
        }
        return contract;
    }

    public Collection<StrategyContract> contracts() {
        return contracts.values();
    }
}
