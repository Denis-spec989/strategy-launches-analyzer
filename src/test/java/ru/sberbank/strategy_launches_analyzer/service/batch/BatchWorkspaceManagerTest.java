package ru.sberbank.strategy_launches_analyzer.service.batch;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.sberbank.strategy_launches_analyzer.config.BatchComparisonProperties;
import ru.sberbank.strategy_launches_analyzer.exceptions.BatchRequestException;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchWorkspaceManagerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void rejectsBatchWhenMinimumFreeSpaceIsUnavailable() {
        BatchComparisonProperties properties = new BatchComparisonProperties();
        properties.setTempDirectory(tempDirectory.resolve("batch-root").toString());
        properties.setMinFreeSpaceBytes(Long.MAX_VALUE);
        BatchWorkspaceManager manager = new BatchWorkspaceManager(
                properties,
                new BatchComparisonMetrics(new SimpleMeterRegistry())
        );
        assertThatThrownBy(manager::initialize)
                .isInstanceOf(BatchRequestException.class)
                .extracting(exception -> ((BatchRequestException) exception).getStatus())
                .isEqualTo(507);
    }

    @Test
    void removesOnlyStaleBatchWorkspacesAtStartup() throws Exception {
        Path root = tempDirectory.resolve("batch-root");
        Path stale = root.resolve("batch-" + UUID.randomUUID());
        Path unrelated = root.resolve("keep-me");
        java.nio.file.Files.createDirectories(stale);
        java.nio.file.Files.writeString(stale.resolve("partial.tmp"), "partial");
        java.nio.file.Files.createDirectories(unrelated);

        BatchComparisonProperties properties = new BatchComparisonProperties();
        properties.setTempDirectory(root.toString());
        properties.setMinFreeSpaceBytes(0);
        BatchWorkspaceManager manager = new BatchWorkspaceManager(
                properties,
                new BatchComparisonMetrics(new SimpleMeterRegistry())
        );

        manager.initialize();

        assertThat(stale).doesNotExist();
        assertThat(unrelated).exists();
    }
}
