package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import chat.giga.client.GigaChatClient;
import chat.giga.model.completion.ChatMessage;
import chat.giga.model.completion.ChatMessageRole;
import chat.giga.model.completion.Choice;
import chat.giga.model.completion.ChoiceFinishReason;
import chat.giga.model.completion.CompletionRequest;
import chat.giga.model.completion.CompletionResponse;
import chat.giga.model.completion.ResponseFormat;
import chat.giga.model.completion.ResponseFormatType;
import chat.giga.model.completion.Usage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.Objects;

public class GigaChatStructuredCompletionClient {
    private final GigaChatClient gigaChatClient;
    private final ObjectMapper objectMapper;

    public GigaChatStructuredCompletionClient(GigaChatClient gigaChatClient, ObjectMapper objectMapper) {
        this.gigaChatClient = gigaChatClient;
        this.objectMapper = objectMapper;
    }

    public <T> GigaChatStructuredCompletionResult<T> complete(
            String model,
            String systemPrompt,
            String userPrompt,
            Class<T> responseType
    ) {
        requireText(model, "model");
        requireText(systemPrompt, "systemPrompt");
        requireText(userPrompt, "userPrompt");
        Objects.requireNonNull(responseType, "responseType must not be null");

        CompletionResponse response = gigaChatClient.completions(
                request(model, systemPrompt, userPrompt, responseType)
        );
        Choice choice = firstCompletedChoice(response);
        String content = choice.message().content();
        if (content == null || content.isBlank()) {
            throw new AgentAnalysisException("GigaChat returned an empty structured response.");
        }

        try {
            return new GigaChatStructuredCompletionResult<>(
                    objectMapper.readValue(content, responseType),
                    tokenUsage(response),
                    response.model()
            );
        } catch (JsonProcessingException ex) {
            throw new AgentAnalysisException("GigaChat returned invalid structured JSON.", ex);
        }
    }

    CompletionRequest request(
            String model,
            String systemPrompt,
            String userPrompt,
            Class<?> responseType
    ) {
        return CompletionRequest.builder()
                .model(model)
                .message(message(ChatMessageRole.SYSTEM, systemPrompt))
                .message(message(ChatMessageRole.USER, userPrompt))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON_SCHEMA)
                        .schema(jsonSchema(responseType))
                        .strict(true)
                        .build())
                .build();
    }

    private JsonNode jsonSchema(Class<?> responseType) {
        String schema = new BeanOutputConverter<>(responseType).getJsonSchema();
        try {
            return objectMapper.readTree(schema);
        } catch (JsonProcessingException ex) {
            throw new AgentAnalysisException("Failed to build the GigaChat structured output schema.", ex);
        }
    }

    private static Choice firstCompletedChoice(CompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AgentAnalysisException("GigaChat returned no completion choices.");
        }
        Choice choice = response.choices().get(0);
        if (choice == null || choice.message() == null) {
            throw new AgentAnalysisException("GigaChat returned an empty completion choice.");
        }
        if (choice.finishReason() != ChoiceFinishReason.STOP) {
            throw new AgentAnalysisException(
                    "GigaChat completion did not finish normally: "
                            + (choice.finishReason() == null ? "not-provided" : choice.finishReason())
                            + "."
            );
        }
        return choice;
    }

    private static ChatMessage message(ChatMessageRole role, String content) {
        return ChatMessage.builder()
                .role(role)
                .content(content)
                .build();
    }

    private static TokenUsage tokenUsage(CompletionResponse response) {
        Usage usage = response.usage();
        if (usage == null) {
            return new TokenUsage(null, null, null, null, null, response.model());
        }
        return new TokenUsage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.precachedPromptTokens() == null ? null : usage.precachedPromptTokens().longValue(),
                null,
                response.model()
        );
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
    }
}
