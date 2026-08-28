package ru.sberbank.strategy_launches_analyzer.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "strategy-launches-analyzer.batch")
public class BatchComparisonProperties {
    @Min(1)
    @Max(1000)
    private int maxItems = 1000;
    @Min(1)
    private long maxLineBytes = 5L * 1024 * 1024;
    @Min(1)
    private long maxRequestBytes = 512L * 1024 * 1024;
    @Min(1)
    private int parallelism = 4;
    @Min(1)
    private int maxInFlight = 8;
    @Min(1)
    private int maxConcurrentBatches = 1;
    @NotNull
    private Duration hardTimeout = Duration.ofMinutes(3);
    @NotBlank
    private String tempDirectory = System.getProperty("java.io.tmpdir")
            + "/strategy-comparison-batches";
    @Min(0)
    private long minFreeSpaceBytes = 1024L * 1024 * 1024;
    @Min(1)
    @Max(1_048_575)
    private int xlsxRowsPerSheet = 1_000_000;
    @Min(128)
    @Max(32_767)
    private int xlsxCellPreviewChars = 2000;

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public long getMaxLineBytes() {
        return maxLineBytes;
    }

    public void setMaxLineBytes(long maxLineBytes) {
        this.maxLineBytes = maxLineBytes;
    }

    public long getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(long maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public int getMaxInFlight() {
        return maxInFlight;
    }

    public void setMaxInFlight(int maxInFlight) {
        this.maxInFlight = maxInFlight;
    }

    public int getMaxConcurrentBatches() {
        return maxConcurrentBatches;
    }

    public void setMaxConcurrentBatches(int maxConcurrentBatches) {
        this.maxConcurrentBatches = maxConcurrentBatches;
    }

    public Duration getHardTimeout() {
        return hardTimeout;
    }

    public void setHardTimeout(Duration hardTimeout) {
        this.hardTimeout = hardTimeout;
    }

    public String getTempDirectory() {
        return tempDirectory;
    }

    public void setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public long getMinFreeSpaceBytes() {
        return minFreeSpaceBytes;
    }

    public void setMinFreeSpaceBytes(long minFreeSpaceBytes) {
        this.minFreeSpaceBytes = minFreeSpaceBytes;
    }

    public int getXlsxRowsPerSheet() {
        return xlsxRowsPerSheet;
    }

    public void setXlsxRowsPerSheet(int xlsxRowsPerSheet) {
        this.xlsxRowsPerSheet = xlsxRowsPerSheet;
    }

    public int getXlsxCellPreviewChars() {
        return xlsxCellPreviewChars;
    }

    public void setXlsxCellPreviewChars(int xlsxCellPreviewChars) {
        this.xlsxCellPreviewChars = xlsxCellPreviewChars;
    }
}
