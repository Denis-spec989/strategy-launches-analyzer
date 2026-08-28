package ru.sberbank.strategy_launches_analyzer.service.batch;

import org.apache.poi.util.TempFileCreationStrategy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class BatchPoiTempFileStrategy implements TempFileCreationStrategy {
    private final ThreadLocal<Path> activeWorkspace = new ThreadLocal<>();

    AutoCloseable activate(Path workspace) {
        activeWorkspace.set(workspace);
        return activeWorkspace::remove;
    }

    @Override
    public File createTempFile(String prefix, String suffix) throws IOException {
        Path directory = requireWorkspace();
        Files.createDirectories(directory);
        return Files.createTempFile(directory, prefix, suffix).toFile();
    }

    @Override
    public File createTempDirectory(String prefix) throws IOException {
        Path directory = requireWorkspace();
        Files.createDirectories(directory);
        return Files.createTempDirectory(directory, prefix).toFile();
    }

    private Path requireWorkspace() throws IOException {
        Path workspace = activeWorkspace.get();
        if (workspace == null) {
            throw new IOException("POI temporary workspace is not active for the current thread.");
        }
        return workspace.resolve("poi");
    }
}
