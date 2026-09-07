package ru.sberbank.strategy_launches_analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyResponse;
import ru.sberbank.strategy_launches_analyzer.dto.api.LaunchMetadata;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContract;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffResult;
import ru.sberbank.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import ru.sberbank.strategy_launches_analyzer.exceptions.BadRequestException;
import ru.sberbank.strategy_launches_analyzer.utils.JsonNodePath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class CompareStrategyLaunchesUseCase {
    private final StrategyDiffEngine diffEngine;
    private final StrategyContractRegistry contractRegistry;
    private final int maxLaunchNodes;
    private final int maxLaunchDepth;

    public CompareStrategyLaunchesUseCase(
            StrategyDiffEngine diffEngine,
            StrategyContractRegistry contractRegistry,
            @Value("${strategy-launches-analyzer.request.max-launch-nodes:5000}") int maxLaunchNodes,
            @Value("${strategy-launches-analyzer.request.max-launch-depth:20}") int maxLaunchDepth
    ) {
        this.diffEngine = diffEngine;
        this.contractRegistry = contractRegistry;
        this.maxLaunchNodes = maxLaunchNodes;
        this.maxLaunchDepth = maxLaunchDepth;
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

        CompareStrategyResponse response = new CompareStrategyResponse(
                contract.strategyName(),
                contract.version(),
                Instant.now(),
                metadata,
                summary,
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        log.info("{} comparison response ready: requestId={}, totalDiffs={}, contractIssueCount={}, "
                        + "deterministicSeverity={}, durationMs={}",
                contract.strategyName(),
                requestId,
                summary.totalDiffs(),
                summary.contractValidationIssues(),
                summary.deterministicSeverity(),
                elapsedMs(startedAt));
        return response;
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
        if (request.metadata().mainStrategyVersion() == null
                || request.metadata().mainStrategyVersion().isBlank()) {
            throw new BadRequestException("metadata.mainStrategyVersion is required.");
        }
        if (request.metadata().shadowStrategyVersion() == null
                || request.metadata().shadowStrategyVersion().isBlank()) {
            throw new BadRequestException("metadata.shadowStrategyVersion is required.");
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
