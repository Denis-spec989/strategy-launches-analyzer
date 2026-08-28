package ru.sberbank.strategy_launches_analyzer.service.batch;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BatchArchive implements AutoCloseable {
    private final UUID batchId;
    private final Path archivePath;
    private final long sizeBytes;
    private final Path workspace;
    private final BatchWorkspaceManager workspaceManager;
    private final Runnable closeCallback;
    private final AtomicBoolean closed = new AtomicBoolean();

    public BatchArchive(
            UUID batchId,
            Path archivePath,
            long sizeBytes,
            Path workspace,
            BatchWorkspaceManager workspaceManager,
            Runnable closeCallback
    ) {
        this.batchId = batchId;
        this.archivePath = archivePath;
        this.sizeBytes = sizeBytes;
        this.workspace = workspace;
        this.workspaceManager = workspaceManager;
        this.closeCallback = closeCallback;
    }

    public UUID batchId() {
        return batchId;
    }

    public Path archivePath() {
        return archivePath;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            workspaceManager.cleanup(workspace);
            closeCallback.run();
        }
    }
}
