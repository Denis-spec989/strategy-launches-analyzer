package com.github.denisspec989.strategy_launches_analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
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
        long startedAt = System.nanoTime();
        log.info(
                "{} comparison request accepted: requestId={}, mainLaunchId={}, shadowLaunchId={}, "
                        + "mainStrategyVersion={}, shadowStrategyVersion={}, launchTimestamp={}, "
                        + "metadataAttributeCount={}, mainRootFieldCount={}, shadowRootFieldCount={}",
                contract.strategyName(),
                requestId,
                valueOrNotProvided(metadata == null ? null : metadata.mainLaunchId()),
                valueOrNotProvided(metadata == null ? null : metadata.shadowLaunchId()),
                valueOrNotProvided(metadata == null ? null : metadata.mainStrategyVersion()),
                valueOrNotProvided(metadata == null ? null : metadata.shadowStrategyVersion()),
                valueOrNotProvided(metadata == null ? null : metadata.launchTimestamp()),
                metadataAttributeCount(metadata),
                rootFieldCount(request.mainLaunch(), contract.rootPath()),
                rootFieldCount(request.shadowLaunch(), contract.rootPath())
        );

        DiffResult diffResult = diffEngine.compare(contract, request.mainLaunch(), request.shadowLaunch());
        ComparisonSummary summary = ComparisonSummary.from(
                contract.strategyName(),
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        log.info("{} deterministic comparison completed: requestId={}, totalDiffs={}, metricDiffs={}, "
                        + "modelDiffs={}, contractTechnicalDiffs={}, contractIssueCount={}, hasCriticalIssues={}, "
                        + "deterministicSeverity={}",
                contract.strategyName(),
                requestId,
                summary.totalDiffs(),
                summary.metricDiffs(),
                summary.modelDiffs(),
                summary.contractTechnicalDiffs(),
                summary.contractValidationIssues(),
                summary.hasCriticalIssues(),
                summary.deterministicSeverity());

        AgentAnalysis agentAnalysis = analyze(contract, summary, request, diffResult);

        CompareStrategyResponse response = new CompareStrategyResponse(
                contract.strategyName(),
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                agentAnalysis
        );
        log.info("{} comparison response ready: requestId={}, totalDiffs={}, contractIssueCount={}, "
                        + "agentStatus={}, agentSeverity={}, durationMs={}",
                contract.strategyName(),
                requestId,
                summary.totalDiffs(),
                summary.contractValidationIssues(),
                agentAnalysis.status(),
                agentAnalysis.overallSeverity(),
                elapsedMs(startedAt));
        return response;
    }

    private AgentAnalysis analyze(
            StrategyContract contract,
            ComparisonSummary summary,
            CompareStrategyRequest request,
            DiffResult diffResult
    ) {
        String requestId = requestId(request.metadata());
        long startedAt = System.nanoTime();
        log.info("{} agent analysis started: requestId={}, agentAnalyzer={}, deterministicSeverity={}, "
                        + "diffCount={}, contractIssueCount={}",
                contract.strategyName(),
                requestId,
                agentAnalyzer.getClass().getSimpleName(),
                summary.deterministicSeverity(),
                diffResult.diffs().size(),
                diffResult.contractValidation().size());
        AgentAnalysisInput input = new AgentAnalysisInput(
                contract.strategyName(),
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                contractContext(contract, diffResult),
                request.metadata()
        );
        try {
            AgentAnalysis analysis = agentAnalyzer.analyze(input);
            TokenUsage tokenUsage = analysis.tokenUsage() == null ? TokenUsage.zero() : analysis.tokenUsage();
            log.info("{} agent analysis completed: requestId={}, status={}, overallSeverity={}, "
                            + "recommendationCount={}, diffExplanationCount={}, inputTokens={}, outputTokens={}, "
                            + "totalTokens={}, model={}, durationMs={}",
                    contract.strategyName(),
                    requestId,
                    analysis.status(),
                    analysis.overallSeverity(),
                    analysis.recommendations() == null ? 0 : analysis.recommendations().size(),
                    analysis.diffExplanations() == null ? 0 : analysis.diffExplanations().size(),
                    tokenUsage.inputTokens(),
                    tokenUsage.outputTokens(),
                    tokenUsage.totalTokens(),
                    valueOrNotProvided(tokenUsage.model()),
                    elapsedMs(startedAt));
            return analysis;
        } catch (AgentAnalysisException ex) {
            throw ex;
        } catch (RuntimeException ex) {
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
        return metadata.requestId();
    }

    private static Object valueOrNotProvided(Object value) {
        return value == null ? "not-provided" : value;
    }

    private static int metadataAttributeCount(LaunchMetadata metadata) {
        if (metadata == null || metadata.attributes() == null) {
            return 0;
        }
        return metadata.attributes().size();
    }

    private static int rootFieldCount(JsonNode launch, String rootPath) {
        JsonNode root = JsonNodePath.at(launch, rootPath);
        return JsonNodePath.isPresent(root) && root.isObject() ? root.size() : 0;
    }

    private static long elapsedMs(long startedAt) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
