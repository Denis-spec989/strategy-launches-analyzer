package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AgentPromptFingerprint {
    private static final int METRIC_HASH_LENGTH = 12;

    private AgentPromptFingerprint() {
    }

    public static String normalPromptHash(ObjectMapper objectMapper) {
        return hash(
                AgentPromptBuilder.SYSTEM_PROMPT,
                AgentPromptBuilder.USER_PROMPT_TEMPLATE,
                StructuredOutputSchema.canonical(objectMapper, StructuredAgentAnalysis.class)
        );
    }

    public static String repairPromptHash(ObjectMapper objectMapper) {
        return hash(
                AgentPromptBuilder.SYSTEM_PROMPT,
                AgentPromptBuilder.REPAIR_PROMPT_TEMPLATE,
                StructuredOutputSchema.canonical(objectMapper, StructuredAgentAnalysis.class)
        );
    }

    public static String metricHash(String fullHash) {
        if (fullHash == null || fullHash.length() < METRIC_HASH_LENGTH) {
            throw new IllegalArgumentException("fullHash must contain at least 12 characters.");
        }
        return fullHash.substring(0, METRIC_HASH_LENGTH);
    }

    private static String hash(String... parts) {
        MessageDigest digest = digest();
        for (String part : parts) {
            digest.update(part.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
