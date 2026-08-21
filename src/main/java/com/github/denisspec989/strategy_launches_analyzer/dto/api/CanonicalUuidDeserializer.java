package com.github.denisspec989.strategy_launches_analyzer.dto.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class CanonicalUuidDeserializer extends JsonDeserializer<UUID> {
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    );

    @Override
    public UUID deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (UUID) context.handleUnexpectedToken(UUID.class, parser);
        }
        String value = parser.getText();
        if (!CANONICAL_UUID.matcher(value).matches()) {
            throw InvalidFormatException.from(
                    parser,
                    "requestId must be a canonical lowercase UUID.",
                    value,
                    UUID.class
            );
        }
        return UUID.fromString(value);
    }
}
