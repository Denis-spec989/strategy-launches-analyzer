package com.github.denisspec989.strategy_launches_analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractFieldContext;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.CompareStrategyResponse;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.LaunchMetadata;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContract;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.BadRequestException;
import com.github.denisspec989.strategy_launches_analyzer.utils.JsonNodePath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompareStrategyLaunchesUseCase {
    private final StrategyDiffEngine diffEngine;
    private final StrategyContractRegistry contractRegistry;
    private final AgentAnalyzer agentAnalyzer;

    public CompareStrategyResponse compare(CompareStrategyRequest request) {
        validateRequest(request);
        StrategyContract contract = contractRegistry.get(request.strategy());
        validateLaunchRoots(request, contract);
        LaunchMetadata metadata = request.metadata();
        String requestId = requestId(metadata);
        log.info(
                "{} comparison request accepted: requestId={}, mainLaunchId={}, shadowLaunchId={}, "
                        + "mainStrategyVersion={}, shadowStrategyVersion={}, launchTimestamp={}",
                contract.strategyName(),
                requestId,
                logValue(metadata == null ? null : metadata.mainLaunchId()),
                logValue(metadata == null ? null : metadata.shadowLaunchId()),
                logValue(metadata == null ? null : metadata.mainStrategyVersion()),
                logValue(metadata == null ? null : metadata.shadowStrategyVersion()),
                logValue(metadata == null ? null : metadata.launchTimestamp())
        );

        DiffResult diffResult = diffEngine.compare(contract, request.mainLaunch(), request.shadowLaunch());
        ComparisonSummary summary = ComparisonSummary.from(
                contract.strategyName(),
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        log.info(
                "{} deterministic comparison completed: requestId={}, totalDiffs={}, metricDiffs={}, "
                        + "modelDiffs={}, contractTechnicalDiffs={}, contractValidationIssues={}, deterministicSeverity={}",
                contract.strategyName(),
                requestId,
                summary.totalDiffs(),
                summary.metricDiffs(),
                summary.modelDiffs(),
                summary.contractTechnicalDiffs(),
                summary.contractValidationIssues(),
                summary.deterministicSeverity()
        );

        AgentAnalysis agentAnalysis = analyze(contract, summary, request, diffResult);

        CompareStrategyResponse response = new CompareStrategyResponse(
                contract.strategyName(),
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                agentAnalysis
        );
        log.info(
                "{} comparison response ready: requestId={}, status={}, overallSeverity={}, totalDiffs={}, contractIssueCount={}",
                contract.strategyName(),
                requestId,
                agentAnalysis.status(),
                agentAnalysis.overallSeverity(),
                diffResult.diffs().size(),
                diffResult.contractValidation().size()
        );
        return response;
    }

    private AgentAnalysis analyze(
            StrategyContract contract,
            ComparisonSummary summary,
            CompareStrategyRequest request,
            DiffResult diffResult
    ) {
        String requestId = requestId(request.metadata());
        AgentAnalysisInput input = new AgentAnalysisInput(
                contract.strategyName(),
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                contractContext(contract, diffResult),
                request.metadata()
        );
        log.info(
                "{} agent analysis started: requestId={}, agentAnalyzer={}, diffCount={}, contractIssueCount={}, "
                        + "contractContextCount={}, deterministicSeverity={}",
                contract.strategyName(),
                requestId,
                agentAnalyzer.getClass().getSimpleName(),
                input.diffs().size(),
                input.contractValidation().size(),
                input.contractContext().size(),
                input.summary().deterministicSeverity()
        );
        long startedAtNanos = System.nanoTime();
        try {
            AgentAnalysis analysis = agentAnalyzer.analyze(input);
            log.info(
                    "{} agent analysis completed: requestId={}, status={}, overallSeverity={}, recommendationCount={}, "
                            + "diffExplanationCount={}, tokenUsage={}, durationMs={}",
                    contract.strategyName(),
                    requestId,
                    analysis.status(),
                    analysis.overallSeverity(),
                    analysis.recommendations() == null ? 0 : analysis.recommendations().size(),
                    analysis.diffExplanations() == null ? 0 : analysis.diffExplanations().size(),
                    tokenUsageSummary(analysis.tokenUsage()),
                    durationMs(startedAtNanos)
            );
            return analysis;
        } catch (AgentAnalysisException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn(
                    "{} agent analysis failed: requestId={}, agentAnalyzer={}, errorType={}, errorMessage={}, durationMs={}",
                    contract.strategyName(),
                    requestId,
                    agentAnalyzer.getClass().getSimpleName(),
                    ex.getClass().getSimpleName(),
                    safeMessage(ex),
                    durationMs(startedAtNanos)
            );
            throw new AgentAnalysisException("Agent analysis failed.", ex);
        }
    }

    private List<ContractFieldContext> contractContext(StrategyContract contract, DiffResult diffResult) {
        java.util.Set<String> touchedPaths = new java.util.LinkedHashSet<>();
        diffResult.diffs().forEach(diff -> touchedPaths.add(diff.path()));
        diffResult.contractValidation().forEach(issue -> touchedPaths.add(issue.path()));
        return contract.fields().stream()
                .filter(field -> touchedPaths.contains(field.path()))
                .map(ContractFieldContext::from)
                .toList();
    }

    private static void validateRequest(CompareStrategyRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required.");
        }
        if (request.strategy() == null) {
            throw new BadRequestException("strategy is required.");
        }
        if (request.mainLaunch() == null || request.mainLaunch().isNull()) {
            throw new BadRequestException("mainLaunch is required.");
        }
        if (request.shadowLaunch() == null || request.shadowLaunch().isNull()) {
            throw new BadRequestException("shadowLaunch is required.");
        }
    }

    private static void validateLaunchRoots(CompareStrategyRequest request, StrategyContract contract) {
        requireLaunchRoot(request.mainLaunch(), "mainLaunch", contract.rootPath());
        requireLaunchRoot(request.shadowLaunch(), "shadowLaunch", contract.rootPath());
    }

    private static void requireLaunchRoot(JsonNode launch, String fieldName, String rootPath) {
        JsonNode root = JsonNodePath.at(launch, rootPath);
        if (!JsonNodePath.isPresent(root) || !root.isObject()) {
            throw new BadRequestException(fieldName + "." + rootPath + " object is required.");
        }
    }

    private static String requestId(LaunchMetadata metadata) {
        if (metadata == null || metadata.requestId() == null || metadata.requestId().isBlank()) {
            return "not-provided";
        }
        return logValue(metadata.requestId());
    }

    private static String logValue(Object value) {
        if (value == null) {
            return "not-provided";
        }
        String text = String.valueOf(value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        return text.length() <= 128 ? text : text.substring(0, 128);
    }

    private static long durationMs(long startedAtNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static String tokenUsageSummary(TokenUsage usage) {
        if (usage == null) {
            return "not-provided";
        }
        return "input=%s, output=%s, total=%s, model=%s".formatted(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                logValue(usage.model())
        );
    }

    private static String safeMessage(RuntimeException ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "not-provided";
        }
        return logValue(ex.getMessage());
    }
}
