package ru.sberbank.strategy_launches_analyzer.service.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.sberbank.strategy_launches_analyzer.config.BatchComparisonProperties;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyResponse;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import ru.sberbank.strategy_launches_analyzer.service.CompareStrategyLaunchesUseCase;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchCompareStrategiesUseCaseTest {
    private static final Instant ANALYZED_AT = Instant.parse("2026-08-27T10:02:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void keepsResultsOrderedAndNeverExceedsConfiguredInFlightWindow() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        BatchComparisonProperties properties = properties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BatchComparisonMetrics metrics = new BatchComparisonMetrics(meterRegistry);
        BatchWorkspaceManager workspaceManager = new BatchWorkspaceManager(
                properties,
                metrics
        );
        workspaceManager.initialize();
        CompareStrategyLaunchesUseCase singleUseCase = mock(CompareStrategyLaunchesUseCase.class);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        when(singleUseCase.compare(any())).thenAnswer(invocation -> {
            CompareStrategyRequest request = invocation.getArgument(0);
            int running = active.incrementAndGet();
            maximumActive.accumulateAndGet(running, Math::max);
            try {
                long requestNumber = request.metadata().requestId().getLeastSignificantBits();
                Thread.sleep(requestNumber == 1L ? 80 : 10);
                return response(request);
            } finally {
                active.decrementAndGet();
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(properties.getParallelism());
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            BatchCompareStrategiesUseCase useCase = new BatchCompareStrategiesUseCase(
                    singleUseCase,
                    new BatchItemParser(objectMapper, validatorFactory.getValidator()),
                    properties,
                    executor,
                    workspaceManager,
                    metrics,
                    objectMapper
            );
            byte[] input = requests(12).getBytes(StandardCharsets.UTF_8);

            try (BatchArchive archive = useCase.compare(new ByteArrayInputStream(input), input.length);
                 ZipFile zip = new ZipFile(archive.archivePath().toFile(), StandardCharsets.UTF_8)) {
                String ndjson = new String(
                        zip.getInputStream(zip.getEntry("results.ndjson")).readAllBytes(),
                        StandardCharsets.UTF_8
                );
                String[] lines = ndjson.strip().split("\\R");

                assertThat(lines).hasSize(12);
                for (int index = 0; index < lines.length; index++) {
                    JsonNode result = objectMapper.readTree(lines[index]);
                    UUID expectedRequestId = new UUID(0L, index + 1L);
                    assertThat(result.path("sequence").asInt()).isEqualTo(index + 1);
                    assertThat(result.path("requestId").asText()).isEqualTo(expectedRequestId.toString());
                    assertThat(result.path("response"))
                            .isEqualTo(objectMapper.valueToTree(responseFor(expectedRequestId)));
                }

                JsonNode manifest = objectMapper.readTree(zip.getInputStream(zip.getEntry("manifest.json")));
                assertThat(manifest.path("unchangedItems").asInt()).isEqualTo(12);
                assertThat(manifest.path("reportedItems").asInt()).isZero();
                try (XSSFWorkbook workbook = new XSSFWorkbook(
                        zip.getInputStream(zip.getEntry("report.xlsx"))
                )) {
                    assertThat(workbook.getSheet("Сводка").getLastRowNum()).isZero();
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(maximumActive).hasValue(properties.getMaxInFlight());
    }

    private BatchComparisonProperties properties() {
        BatchComparisonProperties properties = new BatchComparisonProperties();
        properties.setParallelism(4);
        properties.setMaxInFlight(3);
        properties.setTempDirectory(tempDirectory.resolve("batch-root").toString());
        properties.setMinFreeSpaceBytes(0);
        return properties;
    }

    private static String requests(int count) {
        StringBuilder ndjson = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            ndjson.append("""
                    {"strategy":"LGD_DIGITAL","mainLaunch":{},"shadowLaunch":{},"metadata":{
                      "requestId":"%s",
                      "mainLaunchDt":"2026-08-27T10:00:00Z",
                      "shadowLaunchDt":"2026-08-27T10:01:00Z"
                    }}
                    """.formatted(new UUID(0L, index)).replaceAll("\\R", ""));
            ndjson.append('\n');
        }
        return ndjson.toString();
    }

    private static CompareStrategyResponse response(CompareStrategyRequest request) {
        return new CompareStrategyResponse(
                request.strategy().name(),
                "test-contract",
                ANALYZED_AT,
                request.metadata(),
                new ComparisonSummary(0, 0, 0, 0, 0, 0, false, Severity.INFO),
                List.of(),
                List.of()
        );
    }

    private static CompareStrategyResponse responseFor(UUID requestId) {
        return new CompareStrategyResponse(
                "LGD_DIGITAL",
                "test-contract",
                ANALYZED_AT,
                new ru.sberbank.strategy_launches_analyzer.dto.api.LaunchMetadata(
                        requestId,
                        null,
                        null,
                        Instant.parse("2026-08-27T10:00:00Z"),
                        Instant.parse("2026-08-27T10:01:00Z"),
                        null
                ),
                new ComparisonSummary(0, 0, 0, 0, 0, 0, false, Severity.INFO),
                List.of(),
                List.of()
        );
    }
}
