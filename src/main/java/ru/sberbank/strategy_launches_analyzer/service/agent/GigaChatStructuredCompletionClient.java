package ru.sberbank.strategy_launches_analyzer.service.agent;

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
import ru.sberbank.strategy_launches_analyzer.dto.agent.RepairableAgentResponseReason;
import ru.sberbank.strategy_launches_analyzer.dto.agent.TokenUsage;
import ru.sberbank.strategy_launches_analyzer.exceptions.RepairableAgentResponseException;

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
            throw repairable(
                    "GigaChat returned an empty structured response.",
                    RepairableAgentResponseReason.EMPTY_CONTENT,
                    "response content is blank",
                    response
            );
        }

        try {
            return new GigaChatStructuredCompletionResult<>(
                    objectMapper.readValue(content, responseType),
                    tokenUsage(response),
                    actualModel(response)
            );
        } catch (JsonProcessingException ex) {
            throw repairable(
                    "GigaChat returned invalid structured JSON.",
                    ex,
                    RepairableAgentResponseReason.INVALID_JSON,
                    "response content is not valid JSON",
                    response
            );
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
        return StructuredOutputSchema.create(objectMapper, responseType);
    }

    private static Choice firstCompletedChoice(CompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw repairable(
                    "GigaChat returned no completion choices.",
                    RepairableAgentResponseReason.NO_CHOICE,
                    "response has no completion choice",
                    response
            );
        }
        Choice choice = response.choices().get(0);
        if (choice == null || choice.message() == null) {
            throw repairable(
                    "GigaChat returned an empty completion choice.",
                    RepairableAgentResponseReason.NO_CHOICE,
                    "first completion choice is empty",
                    response
            );
        }
        if (choice.finishReason() != ChoiceFinishReason.STOP) {
            String finishReason = choice.finishReason() == null
                    ? "not-provided"
                    : choice.finishReason().toString();
            throw repairable(
                    "GigaChat completion did not finish normally: "
                            + finishReason + ".",
                    RepairableAgentResponseReason.INCOMPLETE_RESPONSE,
                    "finishReason is " + finishReason,
                    response
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
        if (response == null) {
            return TokenUsage.zero();
        }
        Usage usage = response.usage();
        if (usage == null) {
            return new TokenUsage(null, null, null, null, null, actualModel(response));
        }
        return new TokenUsage(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.precachedPromptTokens() == null ? null : usage.precachedPromptTokens().longValue(),
                null,
                actualModel(response)
        );
    }

    private static RepairableAgentResponseException repairable(
            String message,
            RepairableAgentResponseReason reason,
            String violation,
            CompletionResponse response
    ) {
        return new RepairableAgentResponseException(
                message,
                reason,
                java.util.List.of(violation),
                null,
                tokenUsage(response)
        );
    }

    private static RepairableAgentResponseException repairable(
            String message,
            Throwable cause,
            RepairableAgentResponseReason reason,
            String violation,
            CompletionResponse response
    ) {
        return new RepairableAgentResponseException(
                message,
                cause,
                reason,
                java.util.List.of(violation),
                null,
                tokenUsage(response)
        );
    }

    private static String actualModel(CompletionResponse response) {
        return response == null ? null : response.model();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
    }
}
