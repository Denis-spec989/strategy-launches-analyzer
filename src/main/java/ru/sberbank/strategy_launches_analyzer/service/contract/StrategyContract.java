package ru.sberbank.strategy_launches_analyzer.service.contract;

import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractField;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractValueType;
import ru.sberbank.strategy_launches_analyzer.dto.contract.StrategyContractDefinition;
import ru.sberbank.strategy_launches_analyzer.dto.strategy.StrategyName;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class StrategyContract {
    private final StrategyName strategy;
    private final String version;
    private final String rootPath;
    private final Map<String, ContractField> fields;

    public StrategyContract(StrategyName strategy, StrategyContractDefinition definition) {
        if (!strategy.name().equals(definition.strategyName())) {
            throw new IllegalStateException(
                    "%s contract strategyName mismatch: %s".formatted(strategy.name(), definition.strategyName())
            );
        }

        LinkedHashMap<String, ContractField> contractFields = new LinkedHashMap<>();
        for (ContractField field : definition.fields()) {
            contractFields.put(field.path(), field);
        }
        this.strategy = strategy;
        this.version = definition.version();
        this.rootPath = definition.rootPath();
        this.fields = Collections.unmodifiableMap(contractFields);
    }

    public StrategyName strategy() {
        return strategy;
    }

    public String strategyName() {
        return strategy.name();
    }

    public String version() {
        return version;
    }

    public String rootPath() {
        return rootPath;
    }

    public Collection<ContractField> fields() {
        return fields.values();
    }

    public Collection<ContractField> leafFields() {
        return fields.values().stream()
                .filter(ContractField::isLeaf)
                .toList();
    }

    public Optional<ContractField> field(String path) {
        return Optional.ofNullable(fields.get(path));
    }

    public Set<String> knownPaths() {
        return fields.keySet();
    }

    public Set<String> knownContainerPaths() {
        return fields.values().stream()
                .filter(field -> field.valueType() == ContractValueType.OBJECT)
                .map(ContractField::path)
                .collect(Collectors.toUnmodifiableSet());
    }
}
