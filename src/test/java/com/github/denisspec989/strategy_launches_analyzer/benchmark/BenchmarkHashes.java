package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentPromptFingerprint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class BenchmarkHashes {
    private BenchmarkHashes() {
    }

    static String promptHash() {
        return AgentPromptFingerprint.normalPromptHash(new ObjectMapper().findAndRegisterModules());
    }

    static String judgePromptHash() {
        return sha256((GigaChatSemanticJudge.RUBRIC_VERSION + "\n"
                + GigaChatSemanticJudge.SYSTEM_PROMPT + "\n"
                + GigaChatSemanticJudge.ADJUDICATION_PROMPT).getBytes(StandardCharsets.UTF_8));
    }

    static String datasetHash(Path root) {
        MessageDigest digest = digest();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(path -> {
                        try {
                            digest.update(root.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
                            digest.update(Files.readAllBytes(path));
                        } catch (IOException ex) {
                            throw new DatasetHashException(ex);
                        }
                    });
        } catch (DatasetHashException ex) {
            throw new IllegalStateException("Failed to hash benchmark dataset.", ex.getCause());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to traverse benchmark dataset.", ex);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String fileHash(Path path) {
        try {
            return sha256(Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to hash file " + path.toAbsolutePath(), ex);
        }
    }

    static String textHash(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String gitCommit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 && !output.isBlank() ? output : "unknown";
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "unknown";
        }
    }

    private static String sha256(byte[] value) {
        return HexFormat.of().formatHex(digest().digest(value));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static final class DatasetHashException extends RuntimeException {
        private DatasetHashException(IOException cause) {
            super(cause);
        }
    }
}
