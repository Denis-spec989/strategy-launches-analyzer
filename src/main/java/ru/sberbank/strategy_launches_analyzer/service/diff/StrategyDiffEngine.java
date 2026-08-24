package ru.sberbank.strategy_launches_analyzer.service.diff;

import com.fasterxml.jackson.databind.JsonNode;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DeterministicSeverityCalculator;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractField;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssue;
import ru.sberbank.strategy_launches_analyzer.service.contract.ContractValidator;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractValueType;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContract;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.ComparisonBasis;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffCategory;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffEntry;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffResult;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffType;
import ru.sberbank.strategy_launches_analyzer.utils.JsonNodePath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StrategyDiffEngine {
    private static final int RELATIVE_DELTA_SCALE = 6;
    private static final Pattern JSON_NUMBER = Pattern.compile(
            "-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"
    );

    private final ContractValidator contractValidator;
    private final DeterministicDiffIdGenerator diffIdGenerator;

    public DiffResult compare(
            StrategyContract contract,
            UUID requestId,
            JsonNode mainLaunch,
            JsonNode shadowLaunch
    ) {
        List<DiffDraft> diffs = new ArrayList<>();

        for (ContractField field : contract.leafFields()) {
            addKnownFieldDiff(mainLaunch, shadowLaunch, field, diffs);
        }
        addUnknownShapeDiffs(contract, mainLaunch, shadowLaunch, diffs);

        List<ContractIssue> issues = contractValidator.validateBoth(contract, mainLaunch, shadowLaunch);
        List<DiffEntry> resolvedDiffs = diffs.stream()
                .map(diff -> diff.toEntry(
                        diffIdGenerator.generate(
                                requestId,
                                contract.strategyName(),
                                diff.path(),
                                diff.type(),
                                diff.category(),
                                diff.comparisonBasis()
                        ),
                        DeterministicSeverityCalculator.resolveDiffSeverity(diff.type(), diff.path(), issues)
                ))
                .toList();
        if (resolvedDiffs.stream().map(DiffEntry::id).distinct().count() != resolvedDiffs.size()) {
            throw new IllegalStateException("Deterministic diff ID collision detected.");
        }
        return new DiffResult(resolvedDiffs, issues);
    }

    private void addKnownFieldDiff(
            JsonNode mainLaunch,
            JsonNode shadowLaunch,
            ContractField field,
            List<DiffDraft> diffs
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
                    field.path(),
                    DiffType.FIELD_ADDED_IN_SHADOW,
                    field.category(),
                    null,
                    shadowValue,
                    null,
                    null
            ));
            return;
        }
        if (!shadowPresent) {
            diffs.add(diff(
                    field.path(),
                    DiffType.FIELD_MISSING_IN_SHADOW,
                    field.category(),
                    mainValue,
                    null,
                    null,
                    null
            ));
            return;
        }
        if (mainValue.isNull() && shadowValue.isNull()) {
            return;
        }
        if (mainValue.isNull() || shadowValue.isNull()) {
            DiffType diffType = field.nullable() ? valueChangedType(field.valueType()) : DiffType.NULLABILITY_VIOLATION;
            diffs.add(diff(
                    field.path(),
                    diffType,
                    field.category(),
                    mainValue,
                    shadowValue,
                    null,
                    null
            ));
            return;
        }

        if (!field.valueType().matches(mainValue) || !field.valueType().matches(shadowValue)) {
            if (!sameJsonValue(mainValue, shadowValue)) {
                diffs.add(diff(
                        field.path(),
                        DiffType.TYPE_MISMATCH,
                        field.category(),
                        mainValue,
                        shadowValue,
                        null,
                        null
                ));
            }
            if (field.valueType() == ContractValueType.NUMBER) {
                addCoercedNumericDiff(mainValue, shadowValue, field, diffs);
            }
            return;
        }

        switch (field.valueType()) {
            case NUMBER -> addNumericDiff(mainValue, shadowValue, field, diffs);
            case STRING -> addTextDiff(mainValue, shadowValue, field, diffs);
            case BOOLEAN -> addBooleanDiff(mainValue, shadowValue, field, diffs);
            case OBJECT -> {
                // Object containers are validated through their declared child fields.
            }
        }
    }

    private void addNumericDiff(
            JsonNode mainValue,
            JsonNode shadowValue,
            ContractField field,
            List<DiffDraft> diffs
    ) {
        addNumericDiff(
                mainValue,
                shadowValue,
                mainValue.decimalValue(),
                shadowValue.decimalValue(),
                field,
                diffs,
                null
        );
    }

    private void addCoercedNumericDiff(
            JsonNode mainValue,
            JsonNode shadowValue,
            ContractField field,
            List<DiffDraft> diffs
    ) {
        Optional<BigDecimal> mainNumber = numericInterpretation(mainValue);
        Optional<BigDecimal> shadowNumber = numericInterpretation(shadowValue);
        if (mainNumber.isEmpty() || shadowNumber.isEmpty()) {
            return;
        }

        addNumericDiff(
                mainValue,
                shadowValue,
                mainNumber.get(),
                shadowNumber.get(),
                field,
                diffs,
                ComparisonBasis.COERCED_NUMERIC
        );
    }

    private void addNumericDiff(
            JsonNode mainValue,
            JsonNode shadowValue,
            BigDecimal mainNumber,
            BigDecimal shadowNumber,
            ContractField field,
            List<DiffDraft> diffs,
            ComparisonBasis comparisonBasis
    ) {
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
                field.path(),
                DiffType.NUMERIC_VALUE_CHANGED,
                field.category(),
                mainValue,
                shadowValue,
                absoluteDelta,
                relativeDeltaPercent,
                comparisonBasis
        ));
    }

    private static Optional<BigDecimal> numericInterpretation(JsonNode value) {
        if (value.isNumber()) {
            return Optional.of(value.decimalValue());
        }
        if (!value.isTextual()) {
            return Optional.empty();
        }

        String candidate = value.textValue().trim();
        if (!JSON_NUMBER.matcher(candidate).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(candidate));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private void addTextDiff(
            JsonNode mainValue,
            JsonNode shadowValue,
            ContractField field,
            List<DiffDraft> diffs
    ) {
        if (mainValue.asText().equals(shadowValue.asText())) {
            return;
        }

        diffs.add(diff(
                field.path(),
                DiffType.STRING_VALUE_CHANGED,
                field.category(),
                mainValue,
                shadowValue,
                null,
                null
        ));
    }

    private void addBooleanDiff(
            JsonNode mainValue,
            JsonNode shadowValue,
            ContractField field,
            List<DiffDraft> diffs
    ) {
        if (mainValue.asBoolean() == shadowValue.asBoolean()) {
            return;
        }

        diffs.add(diff(
                field.path(),
                DiffType.BOOLEAN_VALUE_CHANGED,
                field.category(),
                mainValue,
                shadowValue,
                null,
                null
        ));
    }

    private void addUnknownShapeDiffs(
            StrategyContract contract,
            JsonNode mainLaunch,
            JsonNode shadowLaunch,
            List<DiffDraft> diffs
    ) {
        Set<String> mainUnknownPaths = collectUnknownRootPaths(contract, mainLaunch);
        Set<String> shadowUnknownPaths = collectUnknownRootPaths(contract, shadowLaunch);

        for (String shadowOnlyPath : difference(shadowUnknownPaths, mainUnknownPaths)) {
            diffs.add(diff(
                    shadowOnlyPath,
                    DiffType.FIELD_ADDED_IN_SHADOW,
                    DiffCategory.CONTRACT_TECHNICAL,
                    null,
                    JsonNodePath.at(shadowLaunch, shadowOnlyPath),
                    null,
                    null
            ));
        }

        for (String mainOnlyPath : difference(mainUnknownPaths, shadowUnknownPaths)) {
            diffs.add(diff(
                    mainOnlyPath,
                    DiffType.FIELD_ADDED_IN_MAIN,
                    DiffCategory.CONTRACT_TECHNICAL,
                    JsonNodePath.at(mainLaunch, mainOnlyPath),
                    null,
                    null,
                    null
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

    private static DiffDraft diff(
            String path,
            DiffType type,
            DiffCategory category,
            JsonNode mainValue,
            JsonNode shadowValue,
            BigDecimal absoluteDelta,
            BigDecimal relativeDeltaPercent
    ) {
        return diff(
                path,
                type,
                category,
                mainValue,
                shadowValue,
                absoluteDelta,
                relativeDeltaPercent,
                null
        );
    }

    private static DiffDraft diff(
            String path,
            DiffType type,
            DiffCategory category,
            JsonNode mainValue,
            JsonNode shadowValue,
            BigDecimal absoluteDelta,
            BigDecimal relativeDeltaPercent,
            ComparisonBasis comparisonBasis
    ) {
        return new DiffDraft(
                path,
                type,
                category,
                mainValue,
                shadowValue,
                absoluteDelta,
                relativeDeltaPercent,
                comparisonBasis
        );
    }

    private record DiffDraft(
            String path,
            DiffType type,
            DiffCategory category,
            JsonNode mainValue,
            JsonNode shadowValue,
            BigDecimal absoluteDelta,
            BigDecimal relativeDeltaPercent,
            ComparisonBasis comparisonBasis
    ) {
        private DiffEntry toEntry(UUID id, Severity deterministicSeverity) {
            return new DiffEntry(
                    id,
                    path,
                    type,
                    category,
                    mainValue,
                    shadowValue,
                    absoluteDelta,
                    relativeDeltaPercent,
                    comparisonBasis,
                    deterministicSeverity
            );
        }
    }
}
