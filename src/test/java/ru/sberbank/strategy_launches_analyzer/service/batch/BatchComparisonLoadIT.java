package ru.sberbank.strategy_launches_analyzer.service.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.sberbank.strategy_launches_analyzer.TestFixtures;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "strategy-launches-analyzer.batch.temp-directory=target/test-batch-load",
        "strategy-launches-analyzer.batch.min-free-space-bytes=0",
        "logging.level.ru.sberbank.strategy_launches_analyzer.service.CompareStrategyLaunchesUseCase=WARN"
})
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Autowired)
class BatchComparisonLoadIT {
    private final BatchCompareStrategiesUseCase useCase;
    private final ObjectMapper objectMapper;

    @Test
    void processesOneThousandRepresentativePairsWithinTwoMinutes() throws Exception {
        StringBuilder ndjson = new StringBuilder();
        for (int index = 1; index <= 1000; index++) {
            ndjson.append(request(new UUID(1L, index).toString())).append('\n');
        }
        byte[] input = ndjson.toString().getBytes(StandardCharsets.UTF_8);
        Instant startedAt = Instant.now();

        try (BatchArchive archive = useCase.compare(new ByteArrayInputStream(input), input.length)) {
            assertThat(archive.sizeBytes()).isPositive();
            assertThat(Duration.between(startedAt, Instant.now())).isLessThan(Duration.ofMinutes(2));
            try (ZipFile zip = new ZipFile(archive.archivePath().toFile(), StandardCharsets.UTF_8)) {
                String results = new String(
                        zip.getInputStream(zip.getEntry("results.ndjson")).readAllBytes(),
                        StandardCharsets.UTF_8
                );
                String[] lines = results.strip().split("\\R");
                assertThat(lines).hasSize(1000);
                for (int index = 0; index < lines.length; index++) {
                    var result = objectMapper.readTree(lines[index]);
                    assertThat(result.path("sequence").asInt()).isEqualTo(index + 1);
                    assertThat(result.path("requestId").asText())
                            .isEqualTo(new UUID(1L, index + 1L).toString());
                }
            }
        }

        Path tempRoot = Path.of("target", "test-batch-load");
        try (var children = Files.list(tempRoot)) {
            assertThat(children.filter(path -> path.getFileName().toString().startsWith("batch-")))
                    .isEmpty();
        }
    }

    private String request(String requestId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("strategy", "LGD_DIGITAL");
        body.set("mainLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/main.json"
        ));
        body.set("shadowLaunch", TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/shadow.json"
        ));
        body.set("metadata", objectMapper.createObjectNode()
                .put("requestId", requestId)
                .put("mainStrategyVersion", "main-v1")
                .put("shadowStrategyVersion", "shadow-v2")
                .put("mainLaunchDt", "2026-06-04T11:00:00Z")
                .put("shadowLaunchDt", "2026-06-04T11:01:00Z"));
        return objectMapper.writeValueAsString(body);
    }
}
