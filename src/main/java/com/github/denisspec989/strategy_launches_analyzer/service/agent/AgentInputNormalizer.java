package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.LaunchMetadata;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;

import java.util.List;

/**
 * Готовит изоляцию входа LLM: сырые значения diff'ов/issue'ов (для незадекларированных полей это могут быть
 * целые object/array-поддеревья запуска) заменяются компактным дескриптором, а клиентские attributes не передаются.
 * Ответ API при этом сохраняет сырые значения (для UI) — нормализация применяется только к LLM-проекции.
 */
public final class AgentInputNormalizer {
    static final int MAX_VALUE_PREVIEW_LENGTH = 200;

    private AgentInputNormalizer() {
    }

    public static List<DiffEntry> normalizeDiffs(List<DiffEntry> diffs) {
        return diffs.stream().map(AgentInputNormalizer::normalizeDiff).toList();
    }

    public static List<ContractIssue> normalizeIssues(List<ContractIssue> issues) {
        return issues.stream().map(AgentInputNormalizer::normalizeIssue).toList();
    }

    public static LaunchMetadata withoutAttributes(LaunchMetadata metadata) {
        if (metadata == null || metadata.attributes() == null) {
            return metadata;
        }
        return new LaunchMetadata(
                metadata.requestId(),
                metadata.mainLaunchId(),
                metadata.shadowLaunchId(),
                metadata.mainLaunchDt(),
                metadata.shadowLaunchDt(),
                null
        );
    }

    private static DiffEntry normalizeDiff(DiffEntry diff) {
        JsonNode mainValue = normalizeValue(diff.mainValue());
        JsonNode shadowValue = normalizeValue(diff.shadowValue());
        if (mainValue == diff.mainValue() && shadowValue == diff.shadowValue()) {
            return diff;
        }
        return new DiffEntry(
                diff.id(),
                diff.path(),
                diff.type(),
                diff.category(),
                mainValue,
                shadowValue,
                diff.absoluteDelta(),
                diff.relativeDeltaPercent(),
                diff.comparisonBasis(),
                diff.deterministicSeverity()
        );
    }

    private static ContractIssue normalizeIssue(ContractIssue issue) {
        JsonNode actualValue = normalizeValue(issue.actualValue());
        if (actualValue == issue.actualValue()) {
            return issue;
        }
        return new ContractIssue(
                issue.id(),
                issue.side(),
                issue.path(),
                issue.type(),
                issue.severity(),
                issue.expected(),
                issue.actual(),
                actualValue,
                issue.message()
        );
    }

    static JsonNode normalizeValue(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isValueNode()) {
            return value;
        }
        String kind = value.isArray() ? "array" : "object";
        String raw = value.toString();
        String preview = raw.length() > MAX_VALUE_PREVIEW_LENGTH
                ? raw.substring(0, MAX_VALUE_PREVIEW_LENGTH) + "…"
                : raw;
        return TextNode.valueOf(kind + "(size=" + value.size() + "): " + preview);
    }
}
