package ru.sberbank.strategy_launches_analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static JsonNode json(ObjectMapper objectMapper, String path) {
        try {
            return objectMapper.readTree(new ClassPathResource(path).getInputStream());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read fixture " + path, ex);
        }
    }
}
