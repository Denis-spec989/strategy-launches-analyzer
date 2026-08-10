package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class BenchmarkCaseLoader {
    static final Path DEFAULT_DATASET = Path.of("src", "test", "resources", "evals", "lgd-digital");

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    BenchmarkCaseLoader(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    List<BenchmarkCase> load(Path root) {
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Benchmark dataset directory does not exist: " + root.toAbsolutePath());
        }
        try (var paths = Files.list(root)) {
            List<BenchmarkCase> cases = paths
                    .filter(Files::isDirectory)
                    .sorted()
                    .map(this::loadCase)
                    .toList();
            if (cases.isEmpty()) {
                throw new IllegalStateException("Benchmark dataset is empty: " + root.toAbsolutePath());
            }
            long distinctIds = cases.stream().map(BenchmarkCase::id).distinct().count();
            if (distinctIds != cases.size()) {
                throw new IllegalStateException("Benchmark dataset contains duplicate case ids.");
            }
            return cases;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to enumerate benchmark dataset " + root.toAbsolutePath(), ex);
        }
    }

    private BenchmarkCase loadCase(Path directory) {
        Path main = directory.resolve("main.json");
        Path shadow = directory.resolve("shadow.json");
        Path expectationsPath = directory.resolve("expectations.yaml");
        requireRegularFile(main);
        requireRegularFile(shadow);
        requireRegularFile(expectationsPath);
        try {
            BenchmarkExpectations expectations = yamlMapper.readValue(
                    expectationsPath.toFile(), BenchmarkExpectations.class
            );
            String directoryId = directory.getFileName().toString();
            if (expectations.id() == null || !directoryId.equals(expectations.id())) {
                throw new IllegalStateException("Benchmark case id must match directory name: " + directoryId);
            }
            JsonNode mainLaunch = jsonMapper.readTree(main.toFile());
            JsonNode shadowLaunch = jsonMapper.readTree(shadow.toFile());
            if (!mainLaunch.isObject() || !shadowLaunch.isObject()) {
                throw new IllegalStateException("Benchmark launches must be JSON objects: " + directoryId);
            }
            return new BenchmarkCase(
                    expectations.id(),
                    expectations.tags(),
                    mainLaunch,
                    shadowLaunch,
                    expectations.semantic()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load benchmark case " + directory.toAbsolutePath(), ex);
        }
    }

    private static void requireRegularFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Required benchmark file is missing: " + path.toAbsolutePath());
        }
    }
}
