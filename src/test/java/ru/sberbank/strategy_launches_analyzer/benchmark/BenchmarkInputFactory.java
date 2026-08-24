package ru.sberbank.strategy_launches_analyzer.benchmark;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import ru.sberbank.strategy_launches_analyzer.dto.api.LaunchMetadata;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffResult;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractFieldContext;
import ru.sberbank.strategy_launches_analyzer.dto.strategy.StrategyName;
import ru.sberbank.strategy_launches_analyzer.service.agent.AgentInputNormalizer;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContract;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import ru.sberbank.strategy_launches_analyzer.service.diff.StrategyDiffEngine;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.UUID;

final class BenchmarkInputFactory {
    private final StrategyDiffEngine diffEngine;
    private final StrategyContract contract;

    BenchmarkInputFactory(StrategyDiffEngine diffEngine, StrategyContractRegistry contractRegistry) {
        this.diffEngine = diffEngine;
        this.contract = contractRegistry.get(StrategyName.LGD_DIGITAL);
    }

    AgentAnalysisInput create(BenchmarkCase benchmarkCase) {
        UUID requestId = UUID.nameUUIDFromBytes(
                ("benchmark-" + benchmarkCase.id()).getBytes(StandardCharsets.UTF_8)
        );
        DiffResult result = diffEngine.compare(
                contract,
                requestId,
                benchmarkCase.mainLaunch(),
                benchmarkCase.shadowLaunch()
        );
        ComparisonSummary summary = ComparisonSummary.from(result.diffs(), result.contractValidation());
        LinkedHashSet<String> touchedPaths = new LinkedHashSet<>();
        result.diffs().forEach(diff -> touchedPaths.add(diff.path()));
        result.contractValidation().forEach(issue -> touchedPaths.add(issue.path()));
        return new AgentAnalysisInput(
                contract.strategyName(),
                summary,
                AgentInputNormalizer.normalizeDiffs(result.diffs()),
                AgentInputNormalizer.normalizeIssues(result.contractValidation()),
                contract.fields().stream()
                        .filter(field -> touchedPaths.contains(field.path()))
                        .map(ContractFieldContext::from)
                        .toList(),
                new LaunchMetadata(
                        requestId,
                        null,
                        null,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        null
                )
        );
    }

    String contractVersion() {
        return contract.version();
    }
}
