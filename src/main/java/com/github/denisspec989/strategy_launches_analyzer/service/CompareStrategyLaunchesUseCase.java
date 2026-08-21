package com.github.denisspec989.strategy_launches_analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentFallbackReason;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentExecutionFailureException;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentInputNormalizer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentMetrics;
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
import com.github.denisspec989.strategy_launches_analyzer.exceptions.BadRequestException;
import com.github.denisspec989.strategy_launches_analyzer.utils.JsonNodePath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
public class CompareStrategyLaunchesUseCase {
    private final StrategyDiffEngine diffEngine;
    private final StrategyContractRegistry contractRegistry;
    private final AgentAnalyzer agentAnalyzer;
    private final Semaphore agentBulkhead;
    private final int maxLaunchNodes;
    private final int maxLaunchDepth;
    private final AgentMetrics agentMetrics;

    @Autowired
    public CompareStrategyLaunchesUseCase(
            StrategyDiffEngine diffEngine,
            StrategyContractRegistry contractRegistry,
            AgentAnalyzer agentAnalyzer,
            AgentMetrics agentMetrics,
            @Value("${strategy-launches-analyzer.agent.max-concurrent-calls:20}") int maxConcurrentAgentCalls,
            @Value("${strategy-launches-analyzer.request.max-launch-nodes:5000}") int maxLaunchNodes,
            @Value("${strategy-launches-analyzer.request.max-launch-depth:20}") int maxLaunchDepth
    ) {
        this.diffEngine = diffEngine;
        this.contractRegistry = contractRegistry;
        this.agentAnalyzer = agentAnalyzer;
        this.agentBulkhead = new Semaphore(maxConcurrentAgentCalls);
        this.maxLaunchNodes = maxLaunchNodes;
        this.maxLaunchDepth = maxLaunchDepth;
        this.agentMetrics = agentMetrics;
    }

    CompareStrategyLaunchesUseCase(
            StrategyDiffEngine diffEngine,
            StrategyContractRegistry contractRegistry,
            AgentAnalyzer agentAnalyzer,
            int maxConcurrentAgentCalls,
            int maxLaunchNodes,
            int maxLaunchDepth
    ) {
        this(
                diffEngine,
                contractRegistry,
                agentAnalyzer,
                AgentMetrics.noop(),
                maxConcurrentAgentCalls,
                maxLaunchNodes,
                maxLaunchDepth
        );
    }

    public CompareStrategyResponse compare(CompareStrategyRequest request) {
        validateRequest(request);
        StrategyContract contract = contractRegistry.get(request.strategy());
        validateLaunchRoots(request, contract);
        validateLaunchLimits(request.mainLaunch(), "mainLaunch");
        validateLaunchLimits(request.shadowLaunch(), "shadowLaunch");
        LaunchMetadata metadata = request.metadata();
        UUID requestId = metadata.requestId();
        long startedAt = System.nanoTime();
        log.info(
                "{} comparison request accepted: requestId={}, mainLaunchId={}, shadowLaunchId={}, "
                        + "mainLaunchDt={}, shadowLaunchDt={}, metadataAttributeCount={}, "
                        + "mainRootFieldCount={}, shadowRootFieldCount={}",
                contract.strategyName(),
                requestId,
                valueOrNotProvided(metadata.mainLaunchId()),
                valueOrNotProvided(metadata.shadowLaunchId()),
                metadata.mainLaunchDt(),
                metadata.shadowLaunchDt(),
                metadataAttributeCount(metadata),
                rootFieldCount(request.mainLaunch(), contract.rootPath()),
                rootFieldCount(request.shadowLaunch(), contract.rootPath())
        );

        DiffResult diffResult = diffEngine.compare(
                contract,
                requestId,
                request.mainLaunch(),
                request.shadowLaunch()
        );
        ComparisonSummary summary = ComparisonSummary.from(
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        log.info("{} deterministic comparison completed: requestId={}, totalDiffs={}, metricDiffs={}, "
                        + "modelDiffs={}, calculationContextDiffs={}, contractTechnicalDiffs={}, contractIssueCount={}, "
                        + "hasCriticalIssues={}, deterministicSeverity={}",
                contract.strategyName(),
                requestId,
                summary.totalDiffs(),
                summary.metricDiffs(),
                summary.modelDiffs(),
                summary.calculationContextDiffs(),
                summary.contractTechnicalDiffs(),
                summary.contractValidationIssues(),
                summary.hasCriticalIssues(),
                summary.deterministicSeverity());

        AgentAnalysis agentAnalysis = analyze(contract, summary, request, diffResult);

        CompareStrategyResponse response = new CompareStrategyResponse(
                contract.strategyName(),
                contract.version(),
                Instant.now(),
                metadata,
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
        UUID requestId = request.metadata().requestId();
        long startedAt = System.nanoTime();
        log.info("{} agent analysis started: requestId={}, agentAnalyzer={}, deterministicSeverity={}, "
                        + "diffCount={}, contractIssueCount={}",
                contract.strategyName(),
                requestId,
                agentAnalyzer.getClass().getSimpleName(),
                summary.deterministicSeverity(),
                diffResult.diffs().size(),
                diffResult.contractValidation().size());
        boolean acquired = false;
        try {
            AgentAnalysisInput input = new AgentAnalysisInput(
                    contract.strategyName(),
                    summary,
                    AgentInputNormalizer.normalizeDiffs(diffResult.diffs()),
                    AgentInputNormalizer.normalizeIssues(diffResult.contractValidation()),
                    contractContext(contract, diffResult),
                    AgentInputNormalizer.withoutAttributes(request.metadata())
            );
            acquired = agentBulkhead.tryAcquire();
            if (!acquired) {
                log.warn("{} agent analysis rejected: requestId={}, reason=bulkhead-full, availablePermits={}",
                        contract.strategyName(),
                        requestId,
                        agentBulkhead.availablePermits());
                return fallback(
                        contract.strategyName(),
                        AgentFallbackReason.CAPACITY,
                        TokenUsage.zero(),
                        startedAt
                );
            }
            AgentAnalysis analysis = agentAnalyzer.analyze(input);
            log.info("{} agent analysis completed: requestId={}, status={}, overallSeverity={}, "
                            + "recommendationCount={}, diffExplanationCount={}, durationMs={}",
                    contract.strategyName(),
                    requestId,
                    analysis.status(),
                    analysis.overallSeverity(),
                    analysis.recommendations() == null ? 0 : analysis.recommendations().size(),
                    analysis.diffExplanations() == null ? 0 : analysis.diffExplanations().size(),
                    elapsedMs(startedAt));
            return analysis;
        } catch (AgentExecutionFailureException ex) {
            log.error("{} agent analysis failed: requestId={}, reason={}, errorType={}",
                    contract.strategyName(), requestId, ex.reason(), ex.getClass().getSimpleName(), ex);
            return fallback(contract.strategyName(), ex.reason(), ex.tokenUsage(), startedAt);
        } catch (RuntimeException ex) {
            log.error("{} agent analysis failed unexpectedly: requestId={}, errorType={}",
                    contract.strategyName(), requestId, ex.getClass().getSimpleName(), ex);
            return fallback(
                    contract.strategyName(),
                    AgentFallbackReason.INTERNAL,
                    TokenUsage.zero(),
                    startedAt
            );
        } finally {
            if (acquired) {
                agentBulkhead.release();
            }
        }
    }

    private AgentAnalysis fallback(
            String strategyName,
            AgentFallbackReason reason,
            TokenUsage tokenUsage,
            long startedAt
    ) {
        try {
            agentMetrics.recordFallback(strategyName, reason, System.nanoTime() - startedAt, tokenUsage);
        } catch (RuntimeException ex) {
            log.warn("Agent fallback metrics recording failed: strategyName={}, reason={}, errorType={}",
                    strategyName, reason, ex.getClass().getSimpleName());
        }
        return AgentAnalysis.failed(reason);
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
        if (request.metadata() == null) {
            throw new BadRequestException("metadata is required.");
        }
        if (request.metadata().requestId() == null) {
            throw new BadRequestException("metadata.requestId is required.");
        }
        if (request.metadata().mainLaunchDt() == null) {
            throw new BadRequestException("metadata.mainLaunchDt is required.");
        }
        if (request.metadata().shadowLaunchDt() == null) {
            throw new BadRequestException("metadata.shadowLaunchDt is required.");
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

    private void validateLaunchLimits(JsonNode launch, String fieldName) {
        checkLaunchLimits(launch, 1, new int[]{0}, fieldName);
    }

    private void checkLaunchLimits(JsonNode node, int depth, int[] count, String fieldName) {
        if (depth > maxLaunchDepth) {
            throw new BadRequestException(fieldName + " nesting depth exceeds limit " + maxLaunchDepth + ".");
        }
        if (++count[0] > maxLaunchNodes) {
            throw new BadRequestException(fieldName + " exceeds node limit " + maxLaunchNodes + ".");
        }
        for (JsonNode child : node) {
            checkLaunchLimits(child, depth + 1, count, fieldName);
        }
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
