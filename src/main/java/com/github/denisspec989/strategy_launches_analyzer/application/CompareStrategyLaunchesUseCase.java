package com.github.denisspec989.strategy_launches_analyzer.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.agent.ContractFieldContext;
import com.github.denisspec989.strategy_launches_analyzer.api.CompareStrategyRequest;
import com.github.denisspec989.strategy_launches_analyzer.api.CompareStrategyResponse;
import com.github.denisspec989.strategy_launches_analyzer.api.LaunchMetadata;
import com.github.denisspec989.strategy_launches_analyzer.contract.JsonNodePath;
import com.github.denisspec989.strategy_launches_analyzer.contract.LgdDigitalContract;
import com.github.denisspec989.strategy_launches_analyzer.diff.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.diff.LgdDigitalDiffEngine;
import com.github.denisspec989.strategy_launches_analyzer.domain.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.domain.ComparisonSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompareStrategyLaunchesUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompareStrategyLaunchesUseCase.class);

    private final LgdDigitalDiffEngine diffEngine;
    private final LgdDigitalContract contract;
    private final AgentAnalyzer agentAnalyzer;
    private final ObjectMapper objectMapper;

    public CompareStrategyLaunchesUseCase(
            LgdDigitalDiffEngine diffEngine,
            LgdDigitalContract contract,
            AgentAnalyzer agentAnalyzer,
            ObjectMapper objectMapper
    ) {
        this.diffEngine = diffEngine;
        this.contract = contract;
        this.agentAnalyzer = agentAnalyzer;
        this.objectMapper = objectMapper;
    }

    public CompareStrategyResponse compare(CompareStrategyRequest request) {
        validateRequest(request);
        LaunchMetadata metadata = request.metadata();
        String requestId = requestId(metadata);
        LOGGER.info(
                "LGD_DIGITAL comparison request accepted: requestId={}, mainLaunchId={}, shadowLaunchId={}, "
                        + "mainStrategyVersion={}, shadowStrategyVersion={}, launchTimestamp={}",
                requestId,
                valueOrNotProvided(metadata == null ? null : metadata.mainLaunchId()),
                valueOrNotProvided(metadata == null ? null : metadata.shadowLaunchId()),
                valueOrNotProvided(metadata == null ? null : metadata.mainStrategyVersion()),
                valueOrNotProvided(metadata == null ? null : metadata.shadowStrategyVersion()),
                valueOrNotProvided(metadata == null ? null : metadata.launchTimestamp())
        );
        LOGGER.info("LGD_DIGITAL comparison request metadata: requestId={}, metadata={}", requestId, prettyJson(metadata));
        LOGGER.info("LGD_DIGITAL main launch payload: requestId={}, mainLaunch={}", requestId, prettyJson(request.mainLaunch()));
        LOGGER.info("LGD_DIGITAL shadow launch payload: requestId={}, shadowLaunch={}", requestId, prettyJson(request.shadowLaunch()));

        DiffResult diffResult = diffEngine.compare(request.mainLaunch(), request.shadowLaunch());
        ComparisonSummary summary = ComparisonSummary.from(
                LgdDigitalContract.STRATEGY_NAME,
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        LOGGER.info("LGD_DIGITAL deterministic comparison summary: requestId={}, summary={}", requestId, prettyJson(summary));
        LOGGER.info("LGD_DIGITAL deterministic diffs: requestId={}, totalDiffs={}, diffs={}",
                requestId,
                diffResult.diffs().size(),
                prettyJson(diffResult.diffs()));
        LOGGER.info("LGD_DIGITAL contract validation result: requestId={}, issueCount={}, issues={}",
                requestId,
                diffResult.contractValidation().size(),
                prettyJson(diffResult.contractValidation()));

        AgentAnalysis agentAnalysis = analyze(summary, request, diffResult);

        CompareStrategyResponse response = new CompareStrategyResponse(
                LgdDigitalContract.STRATEGY_NAME,
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                agentAnalysis
        );
        LOGGER.info("LGD_DIGITAL comparison response ready: requestId={}, response={}", requestId, prettyJson(response));
        return response;
    }

    private AgentAnalysis analyze(ComparisonSummary summary, CompareStrategyRequest request, DiffResult diffResult) {
        String requestId = requestId(request.metadata());
        LOGGER.info("LGD_DIGITAL agent analysis started: requestId={}, agentAnalyzer={}",
                requestId,
                agentAnalyzer.getClass().getSimpleName());
        AgentAnalysisInput input = new AgentAnalysisInput(
                LgdDigitalContract.STRATEGY_NAME,
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                contractContext(diffResult),
                request.metadata()
        );
        LOGGER.info("LGD_DIGITAL agent analysis input: requestId={}, input={}", requestId, prettyJson(input));
        try {
            AgentAnalysis analysis = agentAnalyzer.analyze(input);
            LOGGER.info("LGD_DIGITAL agent analysis completed: requestId={}, status={}, result={}",
                    requestId,
                    analysis.status(),
                    prettyJson(analysis));
            return analysis;
        } catch (RuntimeException ex) {
            LOGGER.info("LGD_DIGITAL agent analysis failed before response mapping: requestId={}, error={}",
                    requestId,
                    ex.toString());
            return AgentAnalysis.failed(ex.getMessage());
        }
    }

    private List<ContractFieldContext> contractContext(DiffResult diffResult) {
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
