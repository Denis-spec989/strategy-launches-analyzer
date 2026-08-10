package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.LaunchMetadata;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractFieldContext;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentInputNormalizer;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContract;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;

import java.util.LinkedHashSet;

final class BenchmarkInputFactory {
    private final StrategyDiffEngine diffEngine;
    private final StrategyContract contract;

    BenchmarkInputFactory(StrategyDiffEngine diffEngine, StrategyContractRegistry contractRegistry) {
        this.diffEngine = diffEngine;
        this.contract = contractRegistry.get(StrategyName.LGD_DIGITAL);
    }

    AgentAnalysisInput create(BenchmarkCase benchmarkCase) {
        DiffResult result = diffEngine.compare(contract, benchmarkCase.mainLaunch(), benchmarkCase.shadowLaunch());
        ComparisonSummary summary = ComparisonSummary.from(
                contract.strategyName(), result.diffs(), result.contractValidation()
        );
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
                new LaunchMetadata("benchmark-" + benchmarkCase.id(), null, null, null, null)
        );
    }

    String contractVersion() {
        return contract.version();
    }
}
