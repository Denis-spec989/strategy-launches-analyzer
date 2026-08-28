package ru.sberbank.strategy_launches_analyzer.service.batch;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.TempFile;
import org.springframework.stereotype.Component;
import ru.sberbank.strategy_launches_analyzer.config.BatchComparisonProperties;
import ru.sberbank.strategy_launches_analyzer.exceptions.BatchRequestException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@Slf4j
public class BatchWorkspaceManager {
    private static final String WORKSPACE_PREFIX = "batch-";

    private final BatchComparisonProperties properties;
    private final BatchComparisonMetrics metrics;
    private final BatchPoiTempFileStrategy poiTempFileStrategy = new BatchPoiTempFileStrategy();
    private Path root;

    public BatchWorkspaceManager(
            BatchComparisonProperties properties,
            BatchComparisonMetrics metrics
    ) {
        this.properties = properties;
        this.metrics = metrics;
    }

    @PostConstruct
    void initialize() {
        root = Path.of(properties.getTempDirectory()).toAbsolutePath().normalize();
        if (root.getParent() == null) {
            throw new IllegalStateException("Batch temp directory must not be a filesystem root: " + root);
        }
        try {
            Files.createDirectories(root);
            verifyWritable();
            cleanupStaleWorkspaces();
            ensureFreeSpace();
            TempFile.setTempFileCreationStrategy(poiTempFileStrategy);
        } catch (IOException ex) {
            throw new IllegalStateException("Batch temp directory is not usable: " + root, ex);
        }
    }

    public Path createWorkspace(UUID batchId) {
        ensureFreeSpace();
        Path workspace = root.resolve(WORKSPACE_PREFIX + batchId);
        try {
            return Files.createDirectory(workspace);
        } catch (IOException ex) {
            throw storageFailure("Cannot create batch temporary workspace.", ex);
        }
    }

    public AutoCloseable activatePoiWorkspace(Path workspace) {
        return poiTempFileStrategy.activate(workspace);
    }

    public void cleanup(Path workspace) {
        if (workspace == null || !workspace.normalize().startsWith(root)) {
            return;
        }
        try {
            deleteRecursively(workspace);
        } catch (IOException ex) {
            metrics.cleanupFailure();
            log.error("Failed to clean batch workspace: path={}", workspace, ex);
        }
    }

    public BatchRequestException storageFailure(String message, Throwable cause) {
        return new BatchRequestException(507, message, cause);
    }

    private void ensureFreeSpace() {
        try {
            long usableSpace = Files.getFileStore(root).getUsableSpace();
            if (usableSpace < properties.getMinFreeSpaceBytes()) {
                throw new BatchRequestException(
                        507,
                        "Insufficient temporary storage for batch comparison."
                );
            }
        } catch (IOException ex) {
            throw storageFailure("Cannot inspect batch temporary storage.", ex);
        }
    }

    private void verifyWritable() throws IOException {
        Path probe = Files.createTempFile(root, ".write-probe-", ".tmp");
        Files.delete(probe);
    }

    private void cleanupStaleWorkspaces() throws IOException {
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(WORKSPACE_PREFIX))
                    .toList()) {
                try {
                    deleteRecursively(child);
                } catch (IOException ex) {
                    metrics.cleanupFailure();
                    log.warn("Failed to clean stale batch workspace: path={}", child, ex);
                }
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path candidate : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(candidate);
            }
        }
    }
}
