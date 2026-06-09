package com.github.denisspec989.strategy_launches_analyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

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
                valueOrNotProvided(metadata == null ? null : metadata.mainLaunchId()),
                valueOrNotProvided(metadata == null ? null : metadata.shadowLaunchId()),
                valueOrNotProvided(metadata == null ? null : metadata.mainStrategyVersion()),
                valueOrNotProvided(metadata == null ? null : metadata.shadowStrategyVersion()),
                valueOrNotProvided(metadata == null ? null : metadata.launchTimestamp())
        );
        log.info("{} comparison request metadata: requestId={}, metadata={}",
                contract.strategyName(), requestId, prettyJson(metadata));
        log.info("{} main launch payload: requestId={}, mainLaunch={}",
                contract.strategyName(), requestId, prettyJson(request.mainLaunch()));
        log.info("{} shadow launch payload: requestId={}, shadowLaunch={}",
                contract.strategyName(), requestId, prettyJson(request.shadowLaunch()));

        DiffResult diffResult = diffEngine.compare(contract, request.mainLaunch(), request.shadowLaunch());
        ComparisonSummary summary = ComparisonSummary.from(
                contract.strategyName(),
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        log.info("{} deterministic comparison summary: requestId={}, summary={}",
                contract.strategyName(), requestId, prettyJson(summary));
        log.info("{} deterministic diffs: requestId={}, totalDiffs={}, diffs={}",
                contract.strategyName(),
                requestId,
                diffResult.diffs().size(),
                prettyJson(diffResult.diffs()));
        log.info("{} contract validation result: requestId={}, issueCount={}, issues={}",
                contract.strategyName(),
                requestId,
                diffResult.contractValidation().size(),
                prettyJson(diffResult.contractValidation()));

        AgentAnalysis agentAnalysis = analyze(contract, summary, request, diffResult);

        CompareStrategyResponse response = new CompareStrategyResponse(
                contract.strategyName(),
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                agentAnalysis
        );
        log.info("{} comparison response ready: requestId={}, response={}",
                contract.strategyName(), requestId, prettyJson(response));
        return response;
    }

    private AgentAnalysis analyze(
            StrategyContract contract,
            ComparisonSummary summary,
            CompareStrategyRequest request,
            DiffResult diffResult
    ) {
        String requestId = requestId(request.metadata());
        log.info("{} agent analysis started: requestId={}, agentAnalyzer={}",
                contract.strategyName(),
                requestId,
                agentAnalyzer.getClass().getSimpleName());
        AgentAnalysisInput input = new AgentAnalysisInput(
                contract.strategyName(),
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                contractContext(contract, diffResult),
                request.metadata()
        );
        log.info("{} agent analysis input: requestId={}, input={}",
                contract.strategyName(), requestId, prettyJson(input));
        try {
            AgentAnalysis analysis = agentAnalyzer.analyze(input);
            log.info("{} agent analysis completed: requestId={}, status={}, result={}",
                    contract.strategyName(),
                    requestId,
                    analysis.status(),
                    prettyJson(analysis));
            return analysis;
        } catch (RuntimeException ex) {
            log.info("{} agent analysis failed before response mapping: requestId={}, error={}",
                    contract.strategyName(),
                    requestId,
                    ex.toString());
            return AgentAnalysis.failed(ex.getMessage());
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

    private String prettyJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
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
}
