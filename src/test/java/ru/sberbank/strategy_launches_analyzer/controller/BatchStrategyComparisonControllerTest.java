package ru.sberbank.strategy_launches_analyzer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import ru.sberbank.strategy_launches_analyzer.TestFixtures;
import ru.sberbank.strategy_launches_analyzer.config.BatchComparisonProperties;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemErrorCode;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemResult;
import ru.sberbank.strategy_launches_analyzer.exceptions.BatchRequestException;
import ru.sberbank.strategy_launches_analyzer.service.batch.BatchArchive;
import ru.sberbank.strategy_launches_analyzer.service.batch.BatchCompareStrategiesUseCase;
import ru.sberbank.strategy_launches_analyzer.service.batch.BatchExcelReportWriter;
import ru.sberbank.strategy_launches_analyzer.service.batch.BatchReportStatistics;
import ru.sberbank.strategy_launches_analyzer.service.batch.BatchWorkspaceManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "strategy-launches-analyzer.batch.temp-directory=target/test-batch-controller",
        "strategy-launches-analyzer.batch.min-free-space-bytes=0",
        "strategy-launches-analyzer.batch.xlsx-rows-per-sheet=2",
        "logging.level.ru.sberbank.strategy_launches_analyzer.service.CompareStrategyLaunchesUseCase=WARN"
})
@AutoConfigureMockMvc
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Autowired)
class BatchStrategyComparisonControllerTest {
    private static final Path TEMP_ROOT = Path.of("target", "test-batch-controller");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final BatchCompareStrategiesUseCase batchUseCase;
    private final BatchWorkspaceManager workspaceManager;
    private final BatchComparisonProperties batchProperties;
    private final MeterRegistry meterRegistry;

    @Test
    void returnsArchiveWithOrderedResultsAndSplitExcelSheets() throws Exception {
        String valid = requestBody("11111111-1111-1111-1111-111111111111");
        String ndjson = "\uFEFF" + valid + "\r\n\r\n{\"invalid\":\r\n" + valid + "\n";

        byte[] archive = mockMvc.perform(post("/api/v1/strategies/compare/batch")
                        .contentType(BatchStrategyComparisonController.NDJSON_MEDIA_TYPE)
                        .content(ndjson.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(BatchStrategyComparisonController.ZIP_MEDIA_TYPE))
                .andExpect(header().string("X-Batch-Id", org.hamcrest.Matchers.matchesPattern(
                        "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
                )))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith(
                        "attachment; filename=\"strategy-comparison-"
                )))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, byte[]> entries = unzip(archive);
        assertThat(entries).containsOnlyKeys("manifest.json", "report.xlsx", "results.ndjson");

        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        assertThat(manifest.path("formatVersion").asText()).isEqualTo("1.4");
        assertThat(manifest.path("inputItems").asInt()).isEqualTo(3);
        assertThat(manifest.path("completedItems").asInt()).isEqualTo(1);
        assertThat(manifest.path("failedItems").asInt()).isEqualTo(2);
        assertThat(manifest.path("unchangedItems").asInt()).isZero();
        assertThat(manifest.path("reportedItems").asInt()).isEqualTo(3);
        assertThat(manifest.path("totalDiffs").asInt()).isEqualTo(3);

        String[] resultLines = new String(entries.get("results.ndjson"), StandardCharsets.UTF_8)
                .strip()
                .split("\\R");
        assertThat(resultLines).hasSize(3);
        assertThat(objectMapper.readTree(resultLines[0]).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(objectMapper.readTree(resultLines[1]).path("error").path("code").asText())
                .isEqualTo("MALFORMED_JSON");
        assertThat(objectMapper.readTree(resultLines[2]).path("error").path("code").asText())
                .isEqualTo("DUPLICATE_REQUEST_ID");
        assertThat(objectMapper.readTree(resultLines[2]).has("sourceRequest")).isFalse();
        assertThat(objectMapper.readTree(resultLines[0]).path("response").path("summary").path("totalDiffs").asInt())
                .isEqualTo(3);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(entries.get("report.xlsx")))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Сводка");
            assertThat(workbook.getSheet("Сводка").getLastRowNum()).isEqualTo(3);
            assertThat(workbook.getSheet("Сводка").getRow(3).getCell(2).getStringCellValue())
                    .isEqualTo("LGD_DIGITAL");
            assertThat(workbook.getSheet("Сводка").getRow(3).getCell(3).getStringCellValue())
                    .isEqualTo("MAIN-1");
            assertThat(headers(workbook, "Сводка"))
                    .doesNotContain("Request ID", "Версия контракта", "Атрибуты");
            assertThat(headers(workbook, "Различия 1"))
                    .containsSequence("№", "Shadow launch ID", "Diff ID")
                    .doesNotContain("Request ID");
            assertThat(headers(workbook, "Ошибки контракта 1"))
                    .containsSequence("№", "Shadow launch ID", "Issue ID")
                    .doesNotContain("Request ID");
            assertThat(headers(workbook, "Ошибки")).doesNotContain("Request ID");
            assertThat(workbook.getSheet("Различия 1").getLastRowNum()).isEqualTo(2);
            assertThat(workbook.getSheet("Различия 2").getLastRowNum()).isEqualTo(1);
            assertShadowLaunchIds(workbook, "Различия 1", "SHADOW-1");
            assertShadowLaunchIds(workbook, "Различия 2", "SHADOW-1");
            assertThat(workbook.getSheet("Ошибки").getLastRowNum()).isEqualTo(2);
            assertThat(workbook.getSheet("О запуске")).isNotNull();
        }

        try (var children = Files.list(TEMP_ROOT)) {
            assertThat(children.filter(path -> path.getFileName().toString().startsWith("batch-")))
                    .isEmpty();
        }
        assertThat(meterRegistry.find("strategy.launches.batch.active").gauge()).isNotNull();
        assertThat(meterRegistry.find("strategy.launches.batch.requests").counters()).isNotEmpty();
        assertThat(meterRegistry.find("strategy.launches.batch.items").counters()).isNotEmpty();
        assertThat(meterRegistry.find("strategy.launches.batch.duration").timer()).isNotNull();
        assertThat(meterRegistry.find("strategy.launches.batch.input.bytes").summary()).isNotNull();
        assertThat(meterRegistry.find("strategy.launches.batch.output.bytes").summary()).isNotNull();
        assertThat(meterRegistry.find("strategy.launches.batch.temp.cleanup.failures").counter()).isNotNull();
    }

    @Test
    void linksContractErrorsToShadowLaunchInExcel() throws Exception {
        String request = requestBody(
                "55555555-5555-5555-5555-555555555555",
                "fixtures/lgd-digital/required-missing-shadow/shadow.json"
        );

        byte[] archive = mockMvc.perform(post("/api/v1/strategies/compare/batch")
                        .contentType(BatchStrategyComparisonController.NDJSON_MEDIA_TYPE)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                unzip(archive).get("report.xlsx")
        ))) {
            assertThat(headers(workbook, "Ошибки контракта 1"))
                    .containsSequence("№", "Shadow launch ID", "Issue ID");
            assertThat(workbook.getSheet("Ошибки контракта 1").getLastRowNum()).isPositive();
            assertShadowLaunchIds(workbook, "Ошибки контракта 1", "SHADOW-1");
        }
    }

    @Test
    void excludesUnchangedPairsFromExcelButKeepsThemInNdjson() throws Exception {
        String changed = requestBody("33333333-3333-3333-3333-333333333333");
        String unchanged = unchangedRequestBody("44444444-4444-4444-4444-444444444444");
        String ndjson = changed + "\n" + unchanged + "\n{\"invalid\":\n";

        byte[] archive = mockMvc.perform(post("/api/v1/strategies/compare/batch")
                        .contentType(BatchStrategyComparisonController.NDJSON_MEDIA_TYPE)
                        .content(ndjson.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, byte[]> entries = unzip(archive);
        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        assertThat(manifest.path("inputItems").asInt()).isEqualTo(3);
        assertThat(manifest.path("completedItems").asInt()).isEqualTo(2);
        assertThat(manifest.path("failedItems").asInt()).isEqualTo(1);
        assertThat(manifest.path("unchangedItems").asInt()).isEqualTo(1);
        assertThat(manifest.path("reportedItems").asInt()).isEqualTo(2);
        assertThat(manifest.path("severityCounts").path("INFO").asInt()
                + manifest.path("severityCounts").path("WARNING").asInt()
                + manifest.path("severityCounts").path("CRITICAL").asInt()).isEqualTo(1);

        String[] resultLines = new String(entries.get("results.ndjson"), StandardCharsets.UTF_8)
                .strip()
                .split("\\R");
        assertThat(resultLines).hasSize(3);
        assertThat(objectMapper.readTree(resultLines[0]).path("response").path("summary").path("totalDiffs").asInt())
                .isEqualTo(3);
        assertThat(objectMapper.readTree(resultLines[1]).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(objectMapper.readTree(resultLines[1]).path("response").path("summary").path("totalDiffs").asInt())
                .isZero();
        assertThat(objectMapper.readTree(resultLines[2]).path("status").asText()).isEqualTo("FAILED");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(entries.get("report.xlsx")))) {
            assertThat(workbook.getSheet("Сводка").getLastRowNum()).isEqualTo(2);
            assertThat(workbook.getSheet("Сводка").getRow(1).getCell(0).getNumericCellValue()).isEqualTo(1);
            assertThat(workbook.getSheet("Сводка").getRow(2).getCell(0).getNumericCellValue()).isEqualTo(3);
            assertThat(workbook.getSheet("Ошибки").getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("О запуске").getRow(8).getCell(1).getStringCellValue())
                    .isEqualTo("1");
            assertThat(workbook.getSheet("О запуске").getRow(9).getCell(1).getStringCellValue())
                    .isEqualTo("2");
        }
    }

    @Test
    void returnsJsonErrorForEmptyBatch() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/compare/batch")
                        .contentType(BatchStrategyComparisonController.NDJSON_MEDIA_TYPE)
                        .content(" \r\n\t\n"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value(
                        "Batch request must contain at least one non-empty item."
                ));
    }

    @Test
    void returnsArchiveWhenEveryItemFails() throws Exception {
        byte[] invalidUtf8 = {(byte) 0xC3, (byte) 0x28, (byte) '\n'};

        byte[] archive = mockMvc.perform(post("/api/v1/strategies/compare/batch")
                        .contentType(BatchStrategyComparisonController.NDJSON_MEDIA_TYPE)
                        .content(invalidUtf8))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, byte[]> entries = unzip(archive);
        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        assertThat(manifest.path("inputItems").asInt()).isEqualTo(1);
        assertThat(manifest.path("completedItems").asInt()).isZero();
        assertThat(manifest.path("failedItems").asInt()).isEqualTo(1);
        assertThat(manifest.path("unchangedItems").asInt()).isZero();
        assertThat(manifest.path("reportedItems").asInt()).isEqualTo(1);
        JsonNode result = objectMapper.readTree(entries.get("results.ndjson"));
        assertThat(result.path("error").path("code").asText()).isEqualTo("INVALID_ENCODING");
    }

    @Test
    void processesExactlyOneThousandItems() throws Exception {
        StringBuilder ndjson = new StringBuilder();
        for (int index = 1; index <= 1000; index++) {
            ndjson.append(requestBody(new UUID(0L, index).toString())).append('\n');
        }

        byte[] archive = mockMvc.perform(post("/api/v1/strategies/compare/batch")
                        .contentType(BatchStrategyComparisonController.NDJSON_MEDIA_TYPE)
                        .content(ndjson.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        JsonNode manifest = objectMapper.readTree(unzip(archive).get("manifest.json"));
        assertThat(manifest.path("inputItems").asInt()).isEqualTo(1000);
        assertThat(manifest.path("completedItems").asInt()).isEqualTo(1000);
        assertThat(manifest.path("failedItems").asInt()).isZero();
        assertThat(manifest.path("unchangedItems").asInt()).isZero();
        assertThat(manifest.path("reportedItems").asInt()).isEqualTo(1000);
    }

    @Test
    void rejectsSecondBatchUntilFirstArchiveIsClosed() throws Exception {
        byte[] request = requestBody("22222222-2222-2222-2222-222222222222")
                .getBytes(StandardCharsets.UTF_8);

        try (BatchArchive first = batchUseCase.compare(new ByteArrayInputStream(request), request.length)) {
            assertThatThrownBy(() -> batchUseCase.compare(new ByteArrayInputStream(request), request.length))
                    .isInstanceOf(BatchRequestException.class)
                    .extracting(exception -> ((BatchRequestException) exception).getStatus())
                    .isEqualTo(429);
        }

        try (BatchArchive afterRelease = batchUseCase.compare(new ByteArrayInputStream(request), request.length)) {
            assertThat(afterRelease.sizeBytes()).isPositive();
        }
    }

    @Test
    void rejectsDeclaredContentLengthAboveGlobalLimit() {
        assertThatThrownBy(() -> batchUseCase.compare(
                new ByteArrayInputStream(new byte[0]),
                batchProperties.getMaxRequestBytes() + 1
        ))
                .isInstanceOf(BatchRequestException.class)
                .extracting(exception -> ((BatchRequestException) exception).getStatus())
                .isEqualTo(413);
    }

    @Test
    void escapesFormulaAndTruncatesLongExcelText() throws Exception {
        UUID batchId = UUID.randomUUID();
        Path workspace = workspaceManager.createWorkspace(batchId);
        Path report = workspace.resolve("formula.xlsx");
        String dangerousMessage = "=" + "x".repeat(batchProperties.getXlsxCellPreviewChars() + 100);
        BatchItemResult result = BatchItemResult.failed(
                1,
                1,
                UUID.randomUUID(),
                BatchItemErrorCode.VALIDATION_ERROR,
                dangerousMessage
        );
        BatchReportStatistics statistics = new BatchReportStatistics();
        statistics.accept(result);

        try (AutoCloseable ignored = workspaceManager.activatePoiWorkspace(workspace);
             BatchExcelReportWriter writer = new BatchExcelReportWriter(
                     objectMapper,
                     batchProperties,
                     batchId,
                     Instant.parse("2026-08-27T10:00:00Z")
             )) {
            writer.accept(result);
            writer.writeTo(report, statistics, Instant.parse("2026-08-27T10:00:01Z"));
            try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(report))) {
                String value = workbook.getSheet("Ошибки").getRow(1).getCell(3).getStringCellValue();
                assertThat(value).startsWith("'=");
                assertThat(value).contains("усечено");
                assertThat(value).hasSize(batchProperties.getXlsxCellPreviewChars());
            }
        } finally {
            workspaceManager.cleanup(workspace);
        }
    }

    private String requestBody(String requestId) throws Exception {
        return requestBody(requestId, "fixtures/lgd-digital/model-change/shadow.json");
    }

    private String unchangedRequestBody(String requestId) throws Exception {
        return requestBody(requestId, "fixtures/lgd-digital/model-change/main.json");
    }

    private String requestBody(String requestId, String shadowFixture) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("strategy", "LGD_DIGITAL");
        body.set("mainLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/main.json"
        ));
        body.set("shadowLaunch", TestFixtures.json(
                objectMapper,
                shadowFixture
        ));
        body.set("metadata", objectMapper.createObjectNode()
                .put("requestId", requestId)
                .put("mainLaunchId", "MAIN-1")
                .put("shadowLaunchId", "SHADOW-1")
                .put("mainLaunchDt", "2026-06-04T11:00:00Z")
                .put("shadowLaunchDt", "2026-06-04T11:01:00Z"));
        return objectMapper.writeValueAsString(body);
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                input.transferTo(output);
                entries.put(entry.getName(), output.toByteArray());
                input.closeEntry();
            }
        }
        return entries;
    }

    private static List<String> headers(XSSFWorkbook workbook, String sheetName) {
        List<String> headers = new ArrayList<>();
        workbook.getSheet(sheetName).getRow(0).forEach(cell -> headers.add(cell.getStringCellValue()));
        return headers;
    }

    private static void assertShadowLaunchIds(
            XSSFWorkbook workbook,
            String sheetName,
            String expectedShadowLaunchId
    ) {
        for (int rowIndex = 1; rowIndex <= workbook.getSheet(sheetName).getLastRowNum(); rowIndex++) {
            assertThat(workbook.getSheet(sheetName).getRow(rowIndex).getCell(1).getStringCellValue())
                    .isEqualTo(expectedShadowLaunchId);
        }
    }
}
