package com.github.denisspec989.strategy_launches_analyzer.service.diff;

import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonBasis;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffType;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

@Component
public class DeterministicDiffIdGenerator {
    private static final byte NAME_SEPARATOR = 0;

    public UUID generate(
            UUID requestId,
            String strategyName,
            String path,
            DiffType type,
            DiffCategory category,
            ComparisonBasis comparisonBasis
    ) {
        Objects.requireNonNull(requestId, "requestId is required for diff ID generation");
        Objects.requireNonNull(strategyName, "strategyName is required for diff ID generation");
        Objects.requireNonNull(path, "path is required for diff ID generation");
        Objects.requireNonNull(type, "type is required for diff ID generation");
        Objects.requireNonNull(category, "category is required for diff ID generation");

        MessageDigest digest = sha1();
        digest.update(uuidBytes(requestId));
        updateNamePart(digest, strategyName);
        updateNamePart(digest, path);
        updateNamePart(digest, type.name());
        updateNamePart(digest, category.name());
        updateNamePart(digest, comparisonBasis == null ? "" : comparisonBasis.name());

        byte[] hash = digest.digest();
        hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
        hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(hash);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static void updateNamePart(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update(NAME_SEPARATOR);
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is required for UUID v5 generation.", ex);
        }
    }
}
