package ru.sberbank.strategy_launches_analyzer.service.contract;

import com.fasterxml.jackson.databind.JsonNode;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractField;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractValueType;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssue;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssueType;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.LaunchSide;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import ru.sberbank.strategy_launches_analyzer.utils.JsonNodePath;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ContractValidator {
    public List<ContractIssue> validateBoth(StrategyContract contract, JsonNode mainLaunch, JsonNode shadowLaunch) {
        AtomicInteger issueCounter = new AtomicInteger(1);
        List<ContractIssue> issues = new ArrayList<>();
        issues.addAll(validate(contract, mainLaunch, LaunchSide.MAIN, issueCounter));
        issues.addAll(validate(contract, shadowLaunch, LaunchSide.SHADOW, issueCounter));
        return List.copyOf(issues);
    }

    public List<ContractIssue> validate(
            StrategyContract contract,
            JsonNode launch,
            LaunchSide side,
            AtomicInteger issueCounter
    ) {
        List<ContractIssue> issues = new ArrayList<>();

        for (ContractField field : contract.fields()) {
            JsonNode node = JsonNodePath.at(launch, field.path());
            if (!JsonNodePath.isPresent(node)) {
                if (field.required()) {
                    issues.add(issue(
                            issueCounter,
                            side,
                            field.path(),
                            ContractIssueType.REQUIRED_FIELD_MISSING,
                            Severity.CRITICAL,
                            expected(field),
                            "missing",
                            null,
                            "Required field is missing."
                    ));
                }
                continue;
            }

            if (node.isNull()) {
                if (!field.nullable()) {
                    issues.add(issue(
                            issueCounter,
                            side,
                            field.path(),
                            ContractIssueType.NULLABILITY_VIOLATION,
                            field.required() ? Severity.CRITICAL : Severity.WARNING,
                            expected(field),
                            "null",
                            node,
                            "Field is null, but null is not allowed by the contract."
                    ));
                }
                continue;
            }

            if (!field.valueType().matches(node)) {
                issues.add(issue(
                        issueCounter,
                        side,
                        field.path(),
                        ContractIssueType.TYPE_MISMATCH,
                        field.required() ? Severity.CRITICAL : Severity.WARNING,
                        expected(field),
                        ContractValueType.actualTypeOf(node),
                        node,
                        "Field type does not match the %s contract.".formatted(contract.strategyName())
                ));
            }
        }

        addUnknownFieldIssues(contract, launch, side, issueCounter, issues);
        return issues;
    }

    private void addUnknownFieldIssues(
            StrategyContract contract,
            JsonNode launch,
            LaunchSide side,
            AtomicInteger issueCounter,
            List<ContractIssue> issues
    ) {
        JsonNode root = JsonNodePath.at(launch, contract.rootPath());
        if (!JsonNodePath.isPresent(root) || !root.isObject()) {
            return;
        }

        addUnknownFieldIssues(contract, root, contract.rootPath(), side, issueCounter, issues);
    }

    private void addUnknownFieldIssues(
            StrategyContract contract,
            JsonNode node,
            String currentPath,
            LaunchSide side,
            AtomicInteger issueCounter,
            List<ContractIssue> issues
    ) {
        if (!node.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String childPath = currentPath + "." + entry.getKey();
            JsonNode childNode = entry.getValue();
            if (contract.knownPaths().contains(childPath)) {
                addUnknownFieldIssues(contract, childNode, childPath, side, issueCounter, issues);
                continue;
            }

            issues.add(issue(
                    issueCounter,
                    side,
                    childPath,
                    ContractIssueType.UNKNOWN_FIELD,
                    Severity.WARNING,
                    "field declared in %s contract".formatted(contract.strategyName()),
                    ContractValueType.actualTypeOf(childNode),
                    childNode,
                    "Field is not declared in the %s contract.".formatted(contract.strategyName())
            ));
        }
    }

    private static ContractIssue issue(
            AtomicInteger issueCounter,
            LaunchSide side,
            String path,
            ContractIssueType type,
            Severity severity,
            String expected,
            String actual,
            JsonNode actualValue,
            String message
    ) {
        return new ContractIssue(
                "C%03d".formatted(issueCounter.getAndIncrement()),
                side,
                path,
                type,
                severity,
                expected,
                actual,
                actualValue,
                message
        );
    }

    private static String expected(ContractField field) {
        String nullable = field.nullable() ? " or null" : "";
        String cardinality = field.required() ? "1..1" : "0..1";
        return field.valueType().name().toLowerCase() + nullable + ", " + cardinality;
    }
}
