package ru.sberbank.strategy_launches_analyzer.service.batch;

import ru.sberbank.strategy_launches_analyzer.config.BatchComparisonProperties;
import ru.sberbank.strategy_launches_analyzer.exceptions.BatchRequestException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class NdjsonBatchReader {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final InputStream input;
    private final long maxLineBytes;
    private final long maxRequestBytes;
    private final int maxItems;
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private int bufferPosition;
    private int bufferLimit;
    private int lineNumber;
    private int sequence;
    private long totalBytes;

    public NdjsonBatchReader(InputStream input, BatchComparisonProperties properties) {
        this.input = input;
        this.maxLineBytes = properties.getMaxLineBytes();
        this.maxRequestBytes = properties.getMaxRequestBytes();
        this.maxItems = properties.getMaxItems();
    }

    public BatchInputLine next() throws IOException {
        while (true) {
            byte[] line = readPhysicalLine();
            if (line == null) {
                return null;
            }
            lineNumber++;
            line = stripCarriageReturn(line);
            if (lineNumber == 1) {
                line = stripBom(line);
            }
            if (isAsciiBlank(line)) {
                continue;
            }
            sequence++;
            if (sequence > maxItems) {
                throw new BatchRequestException(
                        413,
                        "Batch contains more than " + maxItems + " non-empty items."
                );
            }
            return new BatchInputLine(sequence, lineNumber, line);
        }
    }

    public long totalBytes() {
        return totalBytes;
    }

    private byte[] readPhysicalLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(1024);
        boolean hasBytes = false;
        while (true) {
            int value = readByte();
            if (value == -1) {
                return hasBytes ? line.toByteArray() : null;
            }
            hasBytes = true;
            if (value == '\n') {
                return line.toByteArray();
            }
            if (line.size() >= maxLineBytes) {
                throw new BatchRequestException(
                        413,
                        "NDJSON line " + (lineNumber + 1) + " exceeds byte limit " + maxLineBytes + "."
                );
            }
            line.write(value);
        }
    }

    private int readByte() throws IOException {
        if (bufferPosition >= bufferLimit) {
            bufferLimit = input.read(buffer);
            bufferPosition = 0;
            if (bufferLimit < 0) {
                return -1;
            }
        }
        totalBytes++;
        if (totalBytes > maxRequestBytes) {
            throw new BatchRequestException(
                    413,
                    "Batch request exceeds byte limit " + maxRequestBytes + "."
            );
        }
        return Byte.toUnsignedInt(buffer[bufferPosition++]);
    }

    private static byte[] stripCarriageReturn(byte[] value) {
        if (value.length > 0 && value[value.length - 1] == '\r') {
            return Arrays.copyOf(value, value.length - 1);
        }
        return value;
    }

    private static byte[] stripBom(byte[] value) {
        if (value.length >= UTF_8_BOM.length
                && value[0] == UTF_8_BOM[0]
                && value[1] == UTF_8_BOM[1]
                && value[2] == UTF_8_BOM[2]) {
            return Arrays.copyOfRange(value, UTF_8_BOM.length, value.length);
        }
        return value;
    }

    private static boolean isAsciiBlank(byte[] value) {
        for (byte character : value) {
            if (character != ' ' && character != '\t') {
                return false;
            }
        }
        return true;
    }
}
