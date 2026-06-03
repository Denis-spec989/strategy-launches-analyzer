package com.github.denisspec989.strategy_launches_analyzer.contract;

import com.github.denisspec989.strategy_launches_analyzer.domain.DiffCategory;
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

    private final Map<String, ContractField> fields;

    public LgdDigitalContract() {
        LinkedHashMap<String, ContractField> contractFields = new LinkedHashMap<>();
        add(contractFields, ROOT_PATH, ContractValueType.OBJECT, true, false, DiffCategory.CONTRACT_TECHNICAL);
        add(contractFields, "strategyResponse.lgdData", ContractValueType.OBJECT, true, false, DiffCategory.CONTRACT_TECHNICAL);
        add(contractFields, "strategyResponse.lgdData.lgd", ContractValueType.NUMBER, true, false, DiffCategory.METRIC);
        add(contractFields, "strategyResponse.lgdData.lgdModel", ContractValueType.STRING, true, true, DiffCategory.MODEL);
        add(contractFields, "strategyResponse.lgdData.lgdDt", ContractValueType.NUMBER, true, false, DiffCategory.METRIC);
        add(contractFields, "strategyResponse.calculationInfo", ContractValueType.OBJECT, true, false, DiffCategory.CONTRACT_TECHNICAL);
        add(contractFields, "strategyResponse.calculationInfo.mode", ContractValueType.STRING, true, false, DiffCategory.CONTRACT_TECHNICAL);
        add(contractFields, "strategyResponse.calculationInfo.usingCollateral", ContractValueType.NUMBER, false, false, DiffCategory.CONTRACT_TECHNICAL);
        add(contractFields, "strategyResponse.calculationInfo.type", ContractValueType.STRING, true, false, DiffCategory.CONTRACT_TECHNICAL);
        add(contractFields, "strategyResponse.calculationInfo.scenario", ContractValueType.STRING, true, false, DiffCategory.CALCULATION_CONTEXT);
        add(contractFields, "strategyResponse.calculationInfo.usedDefaultValue", ContractValueType.BOOLEAN, true, false, DiffCategory.CALCULATION_CONTEXT);
        add(contractFields, "strategyResponse.calculationInfo.defaultValueReason", ContractValueType.STRING, true, false, DiffCategory.CALCULATION_CONTEXT);
        this.fields = Collections.unmodifiableMap(contractFields);
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

    private static void add(
            Map<String, ContractField> fields,
            String path,
            ContractValueType valueType,
            boolean required,
            boolean nullable,
            DiffCategory category
    ) {
        fields.put(path, new ContractField(path, valueType, required, nullable, category));
    }
}
