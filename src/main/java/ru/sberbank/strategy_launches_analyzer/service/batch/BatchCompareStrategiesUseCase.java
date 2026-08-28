package ru.sberbank.strategy_launches_analyzer.service.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import ru.sberbank.strategy_launches_analyzer.config.BatchComparisonProperties;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyResponse;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemErrorCode;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemResult;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchManifest;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchManifestFile;
import ru.sberbank.strategy_launches_analyzer.exceptions.BadRequestException;
import ru.sberbank.strategy_launches_analyzer.exceptions.BatchRequestException;
import ru.sberbank.strategy_launches_analyzer.service.CompareStrategyLaunchesUseCase;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class BatchCompareStrategiesUseCase {
    private final CompareStrategyLaunchesUseCase singleComparisonUseCase;
    private final BatchItemParser itemParser;
    private final BatchComparisonProperties properties;
    private final ExecutorService executor;
    private final BatchWorkspaceManager workspaceManager;
    private final BatchComparisonMetrics metrics;
    private final ObjectMapper objectMapper;
    private final Semaphore batchSlots;

    public BatchCompareStrategiesUseCase(
            CompareStrategyLaunchesUseCase singleComparisonUseCase,
            BatchItemParser itemParser,
            BatchComparisonProperties properties,
            ExecutorService batchComparisonExecutor,
            BatchWorkspaceManager workspaceManager,
            BatchComparisonMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.singleComparisonUseCase = singleComparisonUseCase;
        this.itemParser = itemParser;
        this.properties = properties;
        this.executor = batchComparisonExecutor;
        this.workspaceManager = workspaceManager;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.batchSlots = new Semaphore(properties.getMaxConcurrentBatches());
    }

    public BatchArchive compare(InputStream input, long contentLength) {
        if (contentLength > properties.getMaxRequestBytes()) {
            metrics.request("TOO_LARGE");
            throw new BatchRequestException(
                    413,
                    "Batch request exceeds byte limit " + properties.getMaxRequestBytes() + "."
            );
        }
        if (!batchSlots.tryAcquire()) {
            metrics.request("BUSY");
            throw new BatchRequestException(429, "Another batch is already active on this instance.", 30);
        }

        metrics.batchStarted();
        UUID batchId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos + properties.getHardTimeout().toNanos();
        Path workspace = null;
        Deque<PendingBatchItem> pending = new ArrayDeque<>();
        try {
            workspace = workspaceManager.createWorkspace(batchId);
            BatchArchive archive = createArchive(
                    batchId,
                    startedAt,
                    deadlineNanos,
                    input,
                    workspace,
                    pending
            );
            metrics.duration(Duration.ofNanos(System.nanoTime() - startedNanos));
            metrics.outputBytes(archive.sizeBytes());
            metrics.request("COMPLETED");
            log.info("Batch comparison archive ready: batchId={}, sizeBytes={}, durationMs={}",
                    batchId,
                    archive.sizeBytes(),
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
            return archive;
        } catch (BatchRequestException ex) {
            cancelPending(pending);
            workspaceManager.cleanup(workspace);
            releaseBatchSlot();
            metrics.request("HTTP_" + ex.getStatus());
            throw ex;
        } catch (IOException ex) {
            cancelPending(pending);
            workspaceManager.cleanup(workspace);
            releaseBatchSlot();
            metrics.request("STORAGE_ERROR");
            throw workspaceManager.storageFailure("Cannot create batch comparison archive.", ex);
        } catch (RuntimeException ex) {
            cancelPending(pending);
            workspaceManager.cleanup(workspace);
            releaseBatchSlot();
            metrics.request("FAILED");
            throw ex;
        }
    }

    private BatchArchive createArchive(
            UUID batchId,
            Instant startedAt,
            long deadlineNanos,
            InputStream input,
            Path workspace,
            Deque<PendingBatchItem> pending
    ) throws IOException {
        Path resultsPath = workspace.resolve("results.ndjson");
        Path reportPath = workspace.resolve("report.xlsx");
        Path manifestPath = workspace.resolve("manifest.json");
        Path archivePath = workspace.resolve("strategy-comparison-" + batchId + ".zip");
        BatchReportStatistics statistics = new BatchReportStatistics();
        NdjsonBatchReader reader = new NdjsonBatchReader(input, properties);
        Set<UUID> seenRequestIds = new HashSet<>();

        try (AutoCloseable ignored = workspaceManager.activatePoiWorkspace(workspace);
             BufferedWriter resultsWriter = Files.newBufferedWriter(resultsPath, StandardCharsets.UTF_8);
             BatchExcelReportWriter excelWriter = new BatchExcelReportWriter(
                     objectMapper,
                     properties,
                     batchId,
                     startedAt
             )) {
            BatchInputLine inputLine;
            while ((inputLine = readNext(reader)) != null) {
                ensureDeadline(deadlineNanos);
                ParsedBatchItem parsed = itemParser.parse(inputLine, seenRequestIds);
                Future<BatchItemResult> future = parsed.valid()
                        ? submit(batchId, parsed)
                        : CompletableFuture.completedFuture(parsed.failure());
                pending.addLast(new PendingBatchItem(future));
                if (pending.size() >= properties.getMaxInFlight()) {
                    record(await(pending.removeFirst(), deadlineNanos), resultsWriter, excelWriter, statistics);
                }
            }
            while (!pending.isEmpty()) {
                record(await(pending.removeFirst(), deadlineNanos), resultsWriter, excelWriter, statistics);
            }
            if (statistics.inputItems() == 0) {
                throw new BatchRequestException(400, "Batch request must contain at least one non-empty item.");
            }
            resultsWriter.flush();
            Instant completedAt = Instant.now();
            excelWriter.writeTo(reportPath, statistics, completedAt);
            metrics.inputBytes(reader.totalBytes());
            writeManifest(
                    manifestPath,
                    new BatchManifest(
                            BatchManifest.CURRENT_FORMAT_VERSION,
                            batchId,
                            startedAt,
                            completedAt,
                            statistics.inputItems(),
                            statistics.completedItems(),
                            statistics.failedItems(),
                            statistics.unchangedItems(),
                            statistics.reportedItems(),
                            statistics.severityCounts(),
                            statistics.totalDiffs(),
                            statistics.totalContractValidationIssues(),
                            List.of(file(reportPath), file(resultsPath))
                    )
            );
        } catch (BatchRequestException | IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Cannot finalize batch report.", ex);
        }

        writeZip(archivePath, manifestPath, reportPath, resultsPath);
        long archiveSize = Files.size(archivePath);
        return new BatchArchive(
                batchId,
                archivePath,
                archiveSize,
                workspace,
                workspaceManager,
                this::releaseBatchSlot
        );
    }

    private Future<BatchItemResult> submit(UUID batchId, ParsedBatchItem parsed) {
        return executor.submit(() -> compareItem(batchId, parsed));
    }

    private static BatchInputLine readNext(NdjsonBatchReader reader) {
        try {
            return reader.next();
        } catch (BatchRequestException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BatchRequestException(500, "Cannot read batch request.", ex);
        }
    }

    private BatchItemResult compareItem(UUID batchId, ParsedBatchItem parsed) {
        CompareStrategyRequest request = parsed.request();
        UUID requestId = request.metadata() == null ? null : request.metadata().requestId();
        try (MDC.MDCCloseable ignoredBatch = MDC.putCloseable("batchId", batchId.toString());
             MDC.MDCCloseable ignoredSequence = MDC.putCloseable(
                     "sequence",
                     Integer.toString(parsed.inputLine().sequence())
             );
             MDC.MDCCloseable ignoredRequest = MDC.putCloseable("requestId", requestId.toString())) {
            CompareStrategyResponse response = singleComparisonUseCase.compare(request);
            return BatchItemResult.completed(
                    parsed.inputLine().sequence(),
                    parsed.inputLine().lineNumber(),
                    requestId,
                    response
            );
        } catch (BadRequestException ex) {
            return BatchItemResult.failed(
                    parsed.inputLine().sequence(),
                    parsed.inputLine().lineNumber(),
                    requestId,
                    request,
                    BatchItemErrorCode.VALIDATION_ERROR,
                    ex.getMessage()
            );
        } catch (Exception ex) {
            log.error("Batch item failed unexpectedly: batchId={}, sequence={}, requestId={}",
                    batchId,
                    parsed.inputLine().sequence(),
                    requestId,
                    ex);
            return BatchItemResult.failed(
                    parsed.inputLine().sequence(),
                    parsed.inputLine().lineNumber(),
                    requestId,
                    request,
                    BatchItemErrorCode.PROCESSING_ERROR,
                    "Internal processing error."
            );
        }
    }

    private BatchItemResult await(PendingBatchItem pending, long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            pending.future().cancel(true);
            throw timeout();
        }
        try {
            return pending.future().get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            pending.future().cancel(true);
            throw timeout();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BatchRequestException(503, "Batch processing was interrupted.", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Batch worker failed outside item error handling.", ex.getCause());
        }
    }

    private void record(
            BatchItemResult result,
            BufferedWriter resultsWriter,
            BatchExcelReportWriter excelWriter,
            BatchReportStatistics statistics
    ) throws IOException {
        resultsWriter.write(objectMapper.writeValueAsString(result));
        resultsWriter.newLine();
        excelWriter.accept(result);
        statistics.accept(result);
        metrics.item(result);
    }

    private void ensureDeadline(long deadlineNanos) {
        if (System.nanoTime() >= deadlineNanos) {
            throw timeout();
        }
    }

    private static BatchRequestException timeout() {
        return new BatchRequestException(503, "Batch processing exceeded the configured timeout.");
    }

    private void writeManifest(Path target, BatchManifest manifest) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), manifest);
    }

    private static BatchManifestFile file(Path path) throws IOException {
        return new BatchManifestFile(path.getFileName().toString(), Files.size(path), sha256(path));
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void writeZip(Path target, Path... files) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            for (Path file : files) {
                zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private static void cancelPending(Deque<PendingBatchItem> pending) {
        pending.forEach(item -> item.future().cancel(true));
        pending.clear();
    }

    private void releaseBatchSlot() {
        batchSlots.release();
        metrics.batchFinished();
    }
}
