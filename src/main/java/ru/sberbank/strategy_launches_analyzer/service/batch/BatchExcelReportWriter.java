package ru.sberbank.strategy_launches_analyzer.service.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import ru.sberbank.strategy_launches_analyzer.config.BatchComparisonProperties;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyResponse;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemResult;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemStatus;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchManifest;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffEntry;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BatchExcelReportWriter implements AutoCloseable {
    private static final String TRUNCATION_MARKER = "… [усечено; полное значение в results.ndjson]";
    private static final String[] SUMMARY_HEADERS = {
            "№", "Строка NDJSON", "Стратегия",
            "ID основного запуска", "Версия основной стратегии", "Дата основного запуска",
            "ID теневого запуска", "Версия теневой стратегии", "Дата теневого запуска",
            "Статус", "Критичность", "Всего различий", "Метрики", "Модели", "Контекст расчёта",
            "Контракт/техника", "Ошибки контракта", "Код ошибки", "Описание ошибки",
            "Время анализа"
    };
    private static final String[] DIFF_HEADERS = {
            "№", "ID теневого запуска", "Версия основной стратегии", "Версия теневой стратегии",
            "Путь в JSON", "Тип", "Категория", "Критичность", "Значение основной стратегии",
            "Значение теневой стратегии", "Абсолютное отклонение", "Относительное отклонение, %",
            "Основание сравнения"
    };
    private static final String[] VALIDATION_HEADERS = {
            "№", "ID теневого запуска", "Версия основной стратегии", "Версия теневой стратегии",
            "ID нарушения", "Сторона", "Путь в JSON", "Тип", "Критичность", "Ожидалось",
            "Получено", "Фактическое значение", "Сообщение"
    };
    private static final String[] ERROR_HEADERS = {
            "№", "Строка NDJSON", "Код ошибки", "Описание ошибки"
    };

    private final ObjectMapper objectMapper;
    private final BatchComparisonProperties properties;
    private final UUID batchId;
    private final Instant startedAt;
    private final SXSSFWorkbook workbook = new SXSSFWorkbook(100);
    private final CellStyle headerStyle;
    private final CellStyle wrappedStyle;
    private final CellStyle infoStyle;
    private final CellStyle warningStyle;
    private final CellStyle criticalStyle;
    private final List<SheetDescriptor> filterableSheets = new ArrayList<>();
    private final Sheet summarySheet;
    private final Sheet errorsSheet;
    private Sheet diffSheet;
    private Sheet validationSheet;
    private int summaryRows;
    private int errorRows;
    private int diffRows;
    private int validationRows;
    private int diffSheetNumber = 1;
    private int validationSheetNumber = 1;
    private boolean written;
    private boolean closed;

    public BatchExcelReportWriter(
            ObjectMapper objectMapper,
            BatchComparisonProperties properties,
            UUID batchId,
            Instant startedAt
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.batchId = batchId;
        this.startedAt = startedAt;
        workbook.setCompressTempFiles(true);
        headerStyle = createHeaderStyle();
        wrappedStyle = createWrappedStyle();
        infoStyle = createSeverityStyle(IndexedColors.LIGHT_GREEN);
        warningStyle = createSeverityStyle(IndexedColors.LIGHT_YELLOW);
        criticalStyle = createSeverityStyle(IndexedColors.ROSE);
        summarySheet = createTableSheet("Сводка", SUMMARY_HEADERS, summaryWidths());
        diffSheet = createTableSheet("Различия 1", DIFF_HEADERS, diffWidths());
        validationSheet = createTableSheet("Ошибки контракта 1", VALIDATION_HEADERS, validationWidths());
        errorsSheet = createTableSheet("Ошибки", ERROR_HEADERS, errorWidths());
    }

    public void accept(BatchItemResult result) {
        if (!BatchReportStatistics.isIncludedInHumanReadableReport(result)) {
            return;
        }
        writeSummary(result);
        if (result.status() == BatchItemStatus.COMPLETED) {
            for (DiffEntry diff : result.response().diffs()) {
                writeDiff(result, diff);
            }
            for (ContractIssue issue : result.response().contractValidation()) {
                writeValidation(result, issue);
            }
        } else {
            writeError(result);
        }
    }

    public void writeTo(Path target, BatchReportStatistics statistics, Instant completedAt) throws IOException {
        writeRunInfo(statistics, completedAt);
        applyFilters();
        try (OutputStream output = Files.newOutputStream(target)) {
            workbook.write(output);
            written = true;
        } finally {
            close();
        }
    }

    private void writeSummary(BatchItemResult result) {
        Row row = summarySheet.createRow(++summaryRows);
        int column = 0;
        writeNumber(row, column++, result.sequence());
        writeNumber(row, column++, result.lineNumber());
        if (result.response() == null) {
            CompareStrategyRequest request = result.sourceRequest();
            if (request == null) {
                column += 7;
            } else {
                writeText(row, column++, value(request.strategy()));
                if (request.metadata() == null) {
                    column += 6;
                } else {
                    writeText(row, column++, request.metadata().mainLaunchId());
                    writeText(row, column++, request.metadata().mainStrategyVersion());
                    writeText(row, column++, value(request.metadata().mainLaunchDt()));
                    writeText(row, column++, request.metadata().shadowLaunchId());
                    writeText(row, column++, request.metadata().shadowStrategyVersion());
                    writeText(row, column++, value(request.metadata().shadowLaunchDt()));
                }
            }
            writeText(row, column++, result.status().name());
            column++;
            column += 6;
            writeText(row, column++, result.error().code().name());
            writeText(row, column++, result.error().message());
            return;
        }
        CompareStrategyResponse response = result.response();
        writeText(row, column++, response.strategyName());
        writeText(row, column++, response.metadata().mainLaunchId());
        writeText(row, column++, response.metadata().mainStrategyVersion());
        writeText(row, column++, value(response.metadata().mainLaunchDt()));
        writeText(row, column++, response.metadata().shadowLaunchId());
        writeText(row, column++, response.metadata().shadowStrategyVersion());
        writeText(row, column++, value(response.metadata().shadowLaunchDt()));
        writeText(row, column++, result.status().name());
        writeSeverity(row, column++, response.summary().deterministicSeverity());
        writeNumber(row, column++, response.summary().totalDiffs());
        writeNumber(row, column++, response.summary().metricDiffs());
        writeNumber(row, column++, response.summary().modelDiffs());
        writeNumber(row, column++, response.summary().calculationContextDiffs());
        writeNumber(row, column++, response.summary().contractTechnicalDiffs());
        writeNumber(row, column++, response.summary().contractValidationIssues());
        column += 2;
        writeText(row, column++, value(response.analyzedAt()));
    }

    private void writeDiff(BatchItemResult result, DiffEntry diff) {
        if (diffRows >= properties.getXlsxRowsPerSheet()) {
            diffRows = 0;
            diffSheet = createTableSheet("Различия " + (++diffSheetNumber), DIFF_HEADERS, diffWidths());
        }
        Row row = diffSheet.createRow(++diffRows);
        int column = 0;
        writeNumber(row, column++, result.sequence());
        writeText(row, column++, result.response().metadata().shadowLaunchId());
        writeText(row, column++, result.response().metadata().mainStrategyVersion());
        writeText(row, column++, result.response().metadata().shadowStrategyVersion());
        writeText(row, column++, diff.path());
        writeText(row, column++, value(diff.type()));
        writeText(row, column++, value(diff.category()));
        writeSeverity(row, column++, diff.deterministicSeverity());
        writeText(row, column++, json(diff.mainValue()));
        writeText(row, column++, json(diff.shadowValue()));
        writeText(row, column++, value(diff.absoluteDelta()));
        writeText(row, column++, value(diff.relativeDeltaPercent()));
        writeText(row, column, value(diff.comparisonBasis()));
    }

    private void writeValidation(BatchItemResult result, ContractIssue issue) {
        if (validationRows >= properties.getXlsxRowsPerSheet()) {
            validationRows = 0;
            validationSheet = createTableSheet(
                    "Ошибки контракта " + (++validationSheetNumber),
                    VALIDATION_HEADERS,
                    validationWidths()
            );
        }
        Row row = validationSheet.createRow(++validationRows);
        int column = 0;
        writeNumber(row, column++, result.sequence());
        writeText(row, column++, result.response().metadata().shadowLaunchId());
        writeText(row, column++, result.response().metadata().mainStrategyVersion());
        writeText(row, column++, result.response().metadata().shadowStrategyVersion());
        writeText(row, column++, issue.id());
        writeText(row, column++, value(issue.side()));
        writeText(row, column++, issue.path());
        writeText(row, column++, value(issue.type()));
        writeSeverity(row, column++, issue.severity());
        writeText(row, column++, issue.expected());
        writeText(row, column++, issue.actual());
        writeText(row, column++, json(issue.actualValue()));
        writeText(row, column, issue.message());
    }

    private void writeError(BatchItemResult result) {
        Row row = errorsSheet.createRow(++errorRows);
        writeNumber(row, 0, result.sequence());
        writeNumber(row, 1, result.lineNumber());
        writeText(row, 2, result.error().code().name());
        writeText(row, 3, result.error().message());
    }

    private void writeRunInfo(BatchReportStatistics statistics, Instant completedAt) {
        Sheet sheet = workbook.createSheet("О запуске");
        String[][] values = {
                {"Параметр", "Значение"},
                {"Версия формата", BatchManifest.CURRENT_FORMAT_VERSION},
                {"ID пакета", batchId.toString()},
                {"Начало обработки", startedAt.toString()},
                {"Завершение обработки", completedAt.toString()},
                {"Всего входных пар", Integer.toString(statistics.inputItems())},
                {"Успешно обработано", Integer.toString(statistics.completedItems())},
                {"С ошибкой", Integer.toString(statistics.failedItems())},
                {"Без изменений (исключены из XLSX)", Integer.toString(statistics.unchangedItems())},
                {"Включено в XLSX", Integer.toString(statistics.reportedItems())},
                {"Критичность INFO", Integer.toString(statistics.severityCounts().get("INFO"))},
                {"Критичность WARNING", Integer.toString(statistics.severityCounts().get("WARNING"))},
                {"Критичность CRITICAL", Integer.toString(statistics.severityCounts().get("CRITICAL"))},
                {"Всего различий", Long.toString(statistics.totalDiffs())},
                {"Нарушения контракта", Long.toString(statistics.totalContractValidationIssues())}
        };
        for (int index = 0; index < values.length; index++) {
            Row row = sheet.createRow(index);
            writeText(row, 0, values[index][0]);
            writeText(row, 1, values[index][1]);
            if (index == 0) {
                row.getCell(0).setCellStyle(headerStyle);
                row.getCell(1).setCellStyle(headerStyle);
            }
        }
        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 48 * 256);
        sheet.createFreezePane(0, 1);
    }

    private Sheet createTableSheet(String name, String[] headers, int[] widths) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(index, Math.min(widths[index], 255 * 256));
        }
        sheet.createFreezePane(0, 1);
        filterableSheets.add(new SheetDescriptor(sheet, headers.length));
        return sheet;
    }

    private void applyFilters() {
        for (SheetDescriptor descriptor : filterableSheets) {
            descriptor.sheet().setAutoFilter(new CellRangeAddress(
                    0,
                    Math.max(0, descriptor.sheet().getLastRowNum()),
                    0,
                    descriptor.columns() - 1
            ));
        }
    }

    private void writeText(Row row, int column, String value) {
        if (value == null) {
            return;
        }
        Cell cell = row.createCell(column);
        cell.setCellValue(safeExcelText(value));
        cell.setCellStyle(wrappedStyle);
    }

    private static void writeNumber(Row row, int column, long value) {
        row.createCell(column).setCellValue((double) value);
    }

    private void writeSeverity(Row row, int column, Severity severity) {
        Cell cell = row.createCell(column);
        cell.setCellValue(severity.name());
        cell.setCellStyle(switch (severity) {
            case INFO -> infoStyle;
            case WARNING -> warningStyle;
            case CRITICAL -> criticalStyle;
        });
    }

    private String json(Object value) {
        if (value == null || value instanceof JsonNode node && node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "<не удалось сериализовать значение>";
        }
    }

    private String safeExcelText(String value) {
        String safe = startsFormula(value) ? "'" + value : value;
        int maxChars = properties.getXlsxCellPreviewChars();
        if (safe.length() <= maxChars) {
            return safe;
        }
        int prefixLength = Math.max(0, maxChars - TRUNCATION_MARKER.length());
        return safe.substring(0, prefixLength) + TRUNCATION_MARKER;
    }

    private static boolean startsFormula(String value) {
        if (value.isEmpty()) {
            return false;
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> true;
            default -> false;
        };
    }

    private CellStyle createHeaderStyle() {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createWrappedStyle() {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createSeverityStyle(IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static String value(Object value) {
        return value == null ? null : value.toString();
    }

    private static int[] summaryWidths() {
        return widths(10, 14, 20, 22, 24, 26, 22, 24, 26, 14, 12, 16, 12, 12, 20, 20, 18, 28, 60, 26);
    }

    private static int[] diffWidths() {
        return widths(10, 22, 24, 24, 48, 28, 24, 12, 60, 60, 20, 24, 24);
    }

    private static int[] validationWidths() {
        return widths(10, 22, 24, 24, 44, 12, 48, 28, 12, 32, 32, 60, 70);
    }

    private static int[] errorWidths() {
        return widths(10, 14, 30, 80);
    }

    private static int[] widths(int... characterWidths) {
        int[] result = new int[characterWidths.length];
        for (int index = 0; index < characterWidths.length; index++) {
            result[index] = characterWidths[index] * 256;
        }
        return result;
    }

    @Override
    @SuppressWarnings("deprecation") // POI 5.5.1 close() warns on an unwritten SXSSF workbook unless disposed first.
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                if (!written) {
                    workbook.dispose();
                }
            } finally {
                workbook.close();
            }
        }
    }

    private record SheetDescriptor(Sheet sheet, int columns) {
    }
}
