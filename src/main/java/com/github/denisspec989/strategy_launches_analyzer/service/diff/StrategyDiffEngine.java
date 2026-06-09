package com.github.denisspec989.strategy_launches_analyzer.service.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractField;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractValueType;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContract;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffType;
import com.github.denisspec989.strategy_launches_analyzer.utils.JsonNodePath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class StrategyDiffEngine {
    private static final int RELATIVE_DELTA_SCALE = 6;

    private final ContractValidator contractValidator;

    public DiffResult compare(StrategyContract contract, JsonNode mainLaunch, JsonNode shadowLaunch) {
        AtomicInteger diffCounter = new AtomicInteger(1);
        List<DiffEntry> diffs = new ArrayList<>();

        for (ContractField field : contract.leafFields()) {
            addKnownFieldDiff(mainLaunch, shadowLaunch, field, diffCounter, diffs);
        }
        addUnknownShapeDiffs(contract, mainLaunch, shadowLaunch, diffCounter, diffs);

        return new DiffResult(
                List.copyOf(diffs),
                contractValidator.validateBoth(contract, mainLaunch, shadowLaunch)
        );
    }

    private void addKnownFieldDiff(
            JsonNode mainLaunch,
            JsonNode shadowLaunch,
            ContractField field,
            AtomicInteger diffCounter,
            List<DiffEntry> diffs
    ) {
        JsonNode mainValue = JsonNodePath.at(mainLaunch, field.path());
        JsonNode shadowValue = JsonNodePath.at(shadowLaunch, field.path());
        boolean mainPresent = JsonNodePath.isPresent(mainValue);
        boolean shadowPresent = JsonNodePath.isPresent(shadowValue);

        if (!mainPresent && !shadowPresent) {
            return;
        }
        if (!mainPresent) {
            diffs.add(diff(
                    diffCounter,
                    field.path(),
                    DiffType.FIELD_ADDED_IN_SHADOW,
                    field.category(),
                    null,
                    shadowValue,
                    null,
                    null,
                    "Field is absent in main launch and present in shadow launch."
            ));
            return;
        }
        if (!shadowPresent) {
            diffs.add(diff(
                    diffCounter,
                    field.path(),
                    DiffType.FIELD_MISSING_IN_SHADOW,
                    field.category(),
                    mainValue,
                    null,
                    null,
                    null,
                    "Field is present in main launch and absent in shadow launch."
            ));
            return;
        }
        if (mainValue.isNull() && shadowValue.isNull()) {
            return;
        }
        if (mainValue.isNull() || shadowValue.isNull()) {
            DiffType diffType = field.nullable() ? valueChangedType(field.valueType()) : DiffType.NULLABILITY_VIOLATION;
            diffs.add(diff(
                    diffCounter,
                    field.path(),
                    diffType,
                    field.category(),
                    mainValue,
                    shadowValue,
                    null,
                    null,
                    "One launch returned null while the other returned a value."
            ));
            return;
        }

        if (!field.valueType().matches(mainValue) || !field.valueType().matches(shadowValue)) {
            if (!sameJsonValue(mainValue, shadowValue)) {
                diffs.add(diff(
                        diffCounter,
                        field.path(),
                        DiffType.TYPE_MISMATCH,
                        field.category(),
                        mainValue,
                        shadowValue,
                        null,
                        null,
                        "Field value or type differs and at least one launch violates the contract type."
                ));
            }
            return;
        }

        switch (field.valueType()) {
            case NUMBER -> addNumericDiff(mainValue, shadowValue, field, diffCounter, diffs);
            case STRING -> addTextDiff(mainValue, shadowValue, field, diffCounter, diffs);
            case BOOLEAN -> addBooleanDiff(mainValue, shadowValue, field, diffCounter, diffs);
            case OBJECT -> {
                // Object containers are validated through their declared child fields.
            }
        }
    }

    private void addNumericDiff(
            JsonNode mainValue,
            JsonNode shadowValue,
            ContractField field,
            AtomicInteger diffCounter,
            List<DiffEntry> diffs
    ) {
        BigDecimal mainNumber = mainValue.decimalValue();
        BigDecimal shadowNumber = shadowValue.decimalValue();
        if (mainNumber.compareTo(shadowNumber) == 0) {
            return;
        }

        BigDecimal absoluteDelta = shadowNumber.subtract(mainNumber).stripTrailingZeros();
        BigDecimal relativeDeltaPercent = null;
        if (mainNumber.compareTo(BigDecimal.ZERO) != 0) {
            relativeDeltaPercent = absoluteDelta
                    .divide(mainNumber, RELATIVE_DELTA_SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .stripTrailingZeros();
        }

        diffs.add(diff(
                diffCounter,
                field.path(),
                DiffType.NUMERIC_VALUE_CHANGED,
                field.category(),
                mainValue,
                shadowValue,
                absoluteDelta,
                relativeDeltaPercent,
                "Numeric value changed in shadow launch."
        ));
    }

    private void addTextDiff(
            JsonNode mainValue,
            JsonNode shadowValue,
            ContractField field,
            AtomicInteger diffCounter,
            List<DiffEntry> diffs
    ) {
        if (mainValue.asText().equals(shadowValue.asText())) {
            return;
        }

        diffs.add(diff(
                diffCounter,
                field.path(),
                DiffType.STRING_VALUE_CHANGED,
                field.category(),
                mainValue,
                shadowValue,
                null,
                null,
                "String value changed in shadow launch."
        ));
    }

    private void addBooleanDiff(
            JsonNode mainValue,
            JsonNode shadowValue,
            ContractField field,
            AtomicInteger diffCounter,
            List<DiffEntry> diffs
    ) {
        if (mainValue.asBoolean() == shadowValue.asBoolean()) {
            return;
        }

        diffs.add(diff(
                diffCounter,
                field.path(),
                DiffType.BOOLEAN_VALUE_CHANGED,
                field.category(),
                mainValue,
                shadowValue,
                null,
                null,
                "Boolean value changed in shadow launch."
        ));
    }

    private void addUnknownShapeDiffs(
            StrategyContract contract,
            JsonNode mainLaunch,
            JsonNode shadowLaunch,
            AtomicInteger diffCounter,
            List<DiffEntry> diffs
    ) {
        Set<String> mainUnknownPaths = collectUnknownRootPaths(contract, mainLaunch);
        Set<String> shadowUnknownPaths = collectUnknownRootPaths(contract, shadowLaunch);

        for (String shadowOnlyPath : difference(shadowUnknownPaths, mainUnknownPaths)) {
            diffs.add(diff(
                    diffCounter,
                    shadowOnlyPath,
                    DiffType.FIELD_ADDED_IN_SHADOW,
                    DiffCategory.CONTRACT_TECHNICAL,
                    null,
                    JsonNodePath.at(shadowLaunch, shadowOnlyPath),
                    null,
                    null,
                    "Undeclared field is present only in shadow launch."
            ));
        }

        for (String mainOnlyPath : difference(mainUnknownPaths, shadowUnknownPaths)) {
            diffs.add(diff(
                    diffCounter,
                    mainOnlyPath,
                    DiffType.FIELD_ADDED_IN_MAIN,
                    DiffCategory.CONTRACT_TECHNICAL,
                    JsonNodePath.at(mainLaunch, mainOnlyPath),
                    null,
                    null,
                    null,
                    "Undeclared field is present only in main launch."
            ));
        }
    }

    private Set<String> collectUnknownRootPaths(StrategyContract contract, JsonNode launch) {
        JsonNode root = JsonNodePath.at(launch, contract.rootPath());
        if (!JsonNodePath.isPresent(root) || !root.isObject()) {
            return Set.of();
        }

        LinkedHashSet<String> paths = new LinkedHashSet<>();
        collectUnknownRootPaths(contract, root, contract.rootPath(), paths);
        return paths;
    }

    private void collectUnknownRootPaths(
            StrategyContract contract,
            JsonNode node,
            String currentPath,
            Set<String> paths
    ) {
        if (!node.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String childPath = currentPath + "." + entry.getKey();
            if (!contract.knownPaths().contains(childPath)) {
                paths.add(childPath);
                continue;
            }
            collectUnknownRootPaths(contract, entry.getValue(), childPath, paths);
        }
    }

    private static List<String> difference(Set<String> left, Set<String> right) {
        return left.stream()
                .filter(path -> !right.contains(path))
                .toList();
    }

    private static boolean sameJsonValue(JsonNode mainValue, JsonNode shadowValue) {
        if (mainValue.isNumber() && shadowValue.isNumber()) {
            return mainValue.decimalValue().compareTo(shadowValue.decimalValue()) == 0;
        }
        return mainValue.equals(shadowValue);
    }

    private static DiffType valueChangedType(ContractValueType valueType) {
        return switch (valueType) {
            case NUMBER -> DiffType.NUMERIC_VALUE_CHANGED;
            case STRING -> DiffType.STRING_VALUE_CHANGED;
            case BOOLEAN -> DiffType.BOOLEAN_VALUE_CHANGED;
            case OBJECT -> DiffType.TYPE_MISMATCH;
        };
    }

    private static DiffEntry diff(
            AtomicInteger diffCounter,
            String path,
            DiffType type,
            DiffCategory category,
            JsonNode mainValue,
            JsonNode shadowValue,
            BigDecimal absoluteDelta,
            BigDecimal relativeDeltaPercent,
            String description
    ) {
        return new DiffEntry(
                "D%03d".formatted(diffCounter.getAndIncrement()),
                path,
                type,
                category,
                mainValue,
                shadowValue,
                absoluteDelta,
                relativeDeltaPercent,
                description
        );
    }
}
