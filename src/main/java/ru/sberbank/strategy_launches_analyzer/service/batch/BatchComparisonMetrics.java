package ru.sberbank.strategy_launches_analyzer.service.batch;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemResult;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BatchComparisonMetrics {
    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeBatches = new AtomicInteger();
    private final Timer duration;
    private final DistributionSummary inputBytes;
    private final DistributionSummary outputBytes;
    private final Counter cleanupFailures;

    public BatchComparisonMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.duration = Timer.builder("strategy.launches.batch.duration")
                .description("Batch comparison archive generation duration")
                .register(meterRegistry);
        this.inputBytes = DistributionSummary.builder("strategy.launches.batch.input.bytes")
                .baseUnit("bytes")
                .register(meterRegistry);
        this.outputBytes = DistributionSummary.builder("strategy.launches.batch.output.bytes")
                .baseUnit("bytes")
                .register(meterRegistry);
        this.cleanupFailures = Counter.builder("strategy.launches.batch.temp.cleanup.failures")
                .description("Batch temporary workspace cleanup failures")
                .register(meterRegistry);
        Gauge.builder("strategy.launches.batch.active", activeBatches, AtomicInteger::get)
                .register(meterRegistry);
    }

    public void batchStarted() {
        activeBatches.incrementAndGet();
    }

    public void batchFinished() {
        activeBatches.decrementAndGet();
    }

    public void request(String outcome) {
        meterRegistry.counter("strategy.launches.batch.requests", "outcome", outcome).increment();
    }

    public void item(BatchItemResult result) {
        String severity = result.response() == null
                ? "NONE"
                : result.response().summary().deterministicSeverity().name();
        meterRegistry.counter(
                "strategy.launches.batch.items",
                "status", result.status().name(),
                "severity", severity
        ).increment();
    }

    public void duration(Duration value) {
        duration.record(value);
    }

    public void inputBytes(long value) {
        inputBytes.record(value);
    }

    public void outputBytes(long value) {
        outputBytes.record(value);
    }

    public void cleanupFailure() {
        cleanupFailures.increment();
    }
}
