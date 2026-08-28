package ru.sberbank.strategy_launches_analyzer.service.batch;

import org.junit.jupiter.api.Test;
import ru.sberbank.strategy_launches_analyzer.config.BatchComparisonProperties;
import ru.sberbank.strategy_launches_analyzer.exceptions.BatchRequestException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NdjsonBatchReaderTest {
    @Test
    void readsBomCrLfAndSkipsBlankLines() throws Exception {
        byte[] input = ("\uFEFF{\"value\":1}\r\n  \r\n{\"value\":2}")
                .getBytes(StandardCharsets.UTF_8);
        NdjsonBatchReader reader = new NdjsonBatchReader(
                new ByteArrayInputStream(input),
                properties(100, 1024, 4096)
        );

        BatchInputLine first = reader.next();
        BatchInputLine second = reader.next();

        assertThat(first.sequence()).isEqualTo(1);
        assertThat(first.lineNumber()).isEqualTo(1);
        assertThat(new String(first.content(), StandardCharsets.UTF_8)).isEqualTo("{\"value\":1}");
        assertThat(second.sequence()).isEqualTo(2);
        assertThat(second.lineNumber()).isEqualTo(3);
        assertThat(reader.next()).isNull();
        assertThat(reader.totalBytes()).isEqualTo(input.length);
    }

    @Test
    void acceptsExactLineAndRequestLimits() throws Exception {
        NdjsonBatchReader reader = new NdjsonBatchReader(
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),
                properties(1, 3, 3)
        );

        assertThat(new String(reader.next().content(), StandardCharsets.UTF_8)).isEqualTo("abc");
        assertThat(reader.next()).isNull();
    }

    @Test
    void rejectsLineExceedingLimit() {
        NdjsonBatchReader reader = new NdjsonBatchReader(
                new ByteArrayInputStream("abcd".getBytes(StandardCharsets.UTF_8)),
                properties(10, 3, 100)
        );

        assertThatThrownBy(reader::next)
                .isInstanceOf(BatchRequestException.class)
                .extracting(exception -> ((BatchRequestException) exception).getStatus())
                .isEqualTo(413);
    }

    @Test
    void rejectsRequestExceedingLimit() {
        NdjsonBatchReader reader = new NdjsonBatchReader(
                new ByteArrayInputStream("abcd".getBytes(StandardCharsets.UTF_8)),
                properties(10, 100, 3)
        );

        assertThatThrownBy(reader::next)
                .isInstanceOf(BatchRequestException.class)
                .extracting(exception -> ((BatchRequestException) exception).getStatus())
                .isEqualTo(413);
    }

    @Test
    void rejectsMoreThanConfiguredItems() throws Exception {
        NdjsonBatchReader reader = new NdjsonBatchReader(
                new ByteArrayInputStream("{}\n{}\n{}".getBytes(StandardCharsets.UTF_8)),
                properties(2, 100, 100)
        );

        assertThat(reader.next().sequence()).isEqualTo(1);
        assertThat(reader.next().sequence()).isEqualTo(2);
        assertThatThrownBy(reader::next)
                .isInstanceOf(BatchRequestException.class)
                .hasMessageContaining("more than 2");
    }

    private static BatchComparisonProperties properties(int items, long lineBytes, long requestBytes) {
        BatchComparisonProperties properties = new BatchComparisonProperties();
        properties.setMaxItems(items);
        properties.setMaxLineBytes(lineBytes);
        properties.setMaxRequestBytes(requestBytes);
        return properties;
    }
}
