package com.github.denisspec989.strategy_launches_analyzer.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.api.CompareStrategyRequest;
import com.github.denisspec989.strategy_launches_analyzer.api.CompareStrategyResponse;
import com.github.denisspec989.strategy_launches_analyzer.contract.JsonNodePath;
import com.github.denisspec989.strategy_launches_analyzer.contract.LgdDigitalContract;
import com.github.denisspec989.strategy_launches_analyzer.diff.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.diff.LgdDigitalDiffEngine;
import com.github.denisspec989.strategy_launches_analyzer.domain.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.domain.ComparisonSummary;
import org.springframework.stereotype.Service;

@Service
public class CompareStrategyLaunchesUseCase {
    private final LgdDigitalDiffEngine diffEngine;
    private final AgentAnalyzer agentAnalyzer;

    public CompareStrategyLaunchesUseCase(LgdDigitalDiffEngine diffEngine, AgentAnalyzer agentAnalyzer) {
        this.diffEngine = diffEngine;
        this.agentAnalyzer = agentAnalyzer;
    }

    public CompareStrategyResponse compare(CompareStrategyRequest request) {
        validateRequest(request);

        DiffResult diffResult = diffEngine.compare(request.mainLaunch(), request.shadowLaunch());
        ComparisonSummary summary = ComparisonSummary.from(
                LgdDigitalContract.STRATEGY_NAME,
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        AgentAnalysis agentAnalysis = analyze(summary, request, diffResult);

        return new CompareStrategyResponse(
                LgdDigitalContract.STRATEGY_NAME,
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                agentAnalysis
        );
    }

    private AgentAnalysis analyze(ComparisonSummary summary, CompareStrategyRequest request, DiffResult diffResult) {
        try {
            return agentAnalyzer.analyze(new AgentAnalysisInput(
                    LgdDigitalContract.STRATEGY_NAME,
                    summary,
                    diffResult.diffs(),
                    diffResult.contractValidation(),
                    request.metadata()
            ));
        } catch (RuntimeException ex) {
            return AgentAnalysis.failed(ex.getMessage());
        }
    }

    private static void validateRequest(CompareStrategyRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required.");
        }
        requireLaunchRoot(request.mainLaunch(), "mainLaunch");
        requireLaunchRoot(request.shadowLaunch(), "shadowLaunch");
    }

    private static void requireLaunchRoot(JsonNode launch, String fieldName) {
        if (launch == null || launch.isNull()) {
            throw new BadRequestException(fieldName + " is required.");
        }
        JsonNode strategyResponse = JsonNodePath.at(launch, LgdDigitalContract.ROOT_PATH);
        if (!JsonNodePath.isPresent(strategyResponse) || !strategyResponse.isObject()) {
            throw new BadRequestException(fieldName + ".strategyResponse object is required.");
        }
    }
}
