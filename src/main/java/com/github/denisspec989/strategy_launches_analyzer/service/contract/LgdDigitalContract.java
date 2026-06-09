package com.github.denisspec989.strategy_launches_analyzer.service.contract;

import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractField;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractValueType;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.StrategyContractDefinition;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LgdDigitalContract {
    public static final String STRATEGY_NAME = "LGD_DIGITAL";
    public static final String ROOT_PATH = "strategyResponse";
    public static final String OPENAPI_RESOURCE = "openapi/lgd-digital.openapi.yaml";
    public static final String OPENAPI_SCHEMA = "LgdDigitalLaunch";

    private final String version;
    private final Map<String, ContractField> fields;

    public LgdDigitalContract() {
        this(new OpenApiStrategyContractLoader().load(OPENAPI_RESOURCE, OPENAPI_SCHEMA));
    }

    LgdDigitalContract(StrategyContractDefinition definition) {
        if (!STRATEGY_NAME.equals(definition.strategyName())) {
            throw new IllegalStateException("LGD_DIGITAL contract strategyName mismatch: " + definition.strategyName());
        }
        if (!ROOT_PATH.equals(definition.rootPath())) {
            throw new IllegalStateException("LGD_DIGITAL contract rootPath mismatch: " + definition.rootPath());
        }

        LinkedHashMap<String, ContractField> contractFields = new LinkedHashMap<>();
        for (ContractField field : definition.fields()) {
            contractFields.put(field.path(), field);
        }
        this.version = definition.version();
        this.fields = Collections.unmodifiableMap(contractFields);
    }

    public String version() {
        return version;
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
