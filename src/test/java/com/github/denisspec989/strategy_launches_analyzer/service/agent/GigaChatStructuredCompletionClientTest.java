package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import chat.giga.client.GigaChatClient;
import chat.giga.model.completion.ChatMessageRole;
import chat.giga.model.completion.Choice;
import chat.giga.model.completion.ChoiceFinishReason;
import chat.giga.model.completion.ChoiceMessage;
import chat.giga.model.completion.CompletionRequest;
import chat.giga.model.completion.CompletionResponse;
import chat.giga.model.completion.ResponseFormatType;
import chat.giga.model.completion.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.RepairableAgentResponseReason;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.RepairableAgentResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GigaChatStructuredCompletionClientTest {
    private final GigaChatClient sdkClient = mock(GigaChatClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private GigaChatStructuredCompletionClient client;

    @BeforeEach
    void setUp() {
        client = new GigaChatStructuredCompletionClient(sdkClient, objectMapper);
    }

    @Test
    void sendsStrictJsonSchemaAndMapsResponseMetadata() {
        when(sdkClient.completions(any())).thenReturn(response(
                ChoiceFinishReason.STOP,
                """
                        {
                          "overallSeverity": "INFO",
                          "summary": "Отличий нет.",
                          "businessImpact": "Влияние отсутствует.",
                          "technicalRisks": "Риски не обнаружены.",
                          "recommendations": [],
                          "diffExplanations": []
                        }
                        """
        ));

        GigaChatStructuredCompletionResult<StructuredAgentAnalysis> result = client.complete(
                "candidate-model",
                "system prompt",
                "user prompt",
                StructuredAgentAnalysis.class
        );

        ArgumentCaptor<CompletionRequest> request = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(sdkClient).completions(request.capture());
        assertThat(request.getValue().model()).isEqualTo("candidate-model");
        assertThat(request.getValue().messages()).extracting(message -> message.role())
                .containsExactly(ChatMessageRole.SYSTEM, ChatMessageRole.USER);
        assertThat(request.getValue().messages()).extracting(message -> message.content())
                .containsExactly("system prompt", "user prompt");
        assertThat(request.getValue().responseFormat().type()).isEqualTo(ResponseFormatType.JSON_SCHEMA);
        assertThat(request.getValue().responseFormat().strict()).isTrue();
        JsonNode schema = (JsonNode) request.getValue().responseFormat().schema();
        assertThat(schema.path("properties").has("overallSeverity")).isTrue();
        assertThat(result.entity().overallSeverity()).isEqualTo(Severity.INFO);
        assertThat(result.tokenUsage().inputTokens()).isEqualTo(11);
        assertThat(result.tokenUsage().outputTokens()).isEqualTo(7);
        assertThat(result.tokenUsage().totalTokens()).isEqualTo(18);
        assertThat(result.tokenUsage().cacheReadInputTokens()).isEqualTo(3L);
        assertThat(result.tokenUsage().cacheWriteInputTokens()).isNull();
        assertThat(result.tokenUsage().model()).isEqualTo("candidate-model:1.2.3");
        assertThat(result.actualModel()).isEqualTo("candidate-model:1.2.3");
    }

    @Test
    void rejectsMissingChoices() {
        when(sdkClient.completions(any())).thenReturn(CompletionResponse.builder().build());

        assertThatThrownBy(() -> complete())
                .isInstanceOfSatisfying(RepairableAgentResponseException.class, ex -> {
                    assertThat(ex).hasMessageContaining("no completion choices");
                    assertThat(ex.reason()).isEqualTo(RepairableAgentResponseReason.NO_CHOICE);
                });
    }

    @Test
    void rejectsNonStopFinishReason() {
        when(sdkClient.completions(any())).thenReturn(response(ChoiceFinishReason.LENGTH, "{}"));

        assertThatThrownBy(() -> complete())
                .isInstanceOfSatisfying(RepairableAgentResponseException.class, ex -> {
                    assertThat(ex).hasMessageContaining("did not finish normally", "length");
                    assertThat(ex.reason()).isEqualTo(RepairableAgentResponseReason.INCOMPLETE_RESPONSE);
                    assertThat(ex.tokenUsage().totalTokens()).isEqualTo(18);
                });
    }

    @Test
    void rejectsBlankContent() {
        when(sdkClient.completions(any())).thenReturn(response(ChoiceFinishReason.STOP, " "));

        assertThatThrownBy(() -> complete())
                .isInstanceOfSatisfying(RepairableAgentResponseException.class, ex -> {
                    assertThat(ex).hasMessageContaining("empty structured response");
                    assertThat(ex.reason()).isEqualTo(RepairableAgentResponseReason.EMPTY_CONTENT);
                });
    }

    @Test
    void rejectsMalformedStructuredJson() {
        when(sdkClient.completions(any())).thenReturn(response(ChoiceFinishReason.STOP, "{invalid"));

        assertThatThrownBy(() -> complete())
                .isInstanceOfSatisfying(RepairableAgentResponseException.class, ex -> {
                    assertThat(ex).hasMessageContaining("invalid structured JSON");
                    assertThat(ex.reason()).isEqualTo(RepairableAgentResponseReason.INVALID_JSON);
                    assertThat(ex.previousResponse()).isNull();
                });
    }

    private void complete() {
        client.complete("model", "system", "user", StructuredAgentAnalysis.class);
    }

    private static CompletionResponse response(ChoiceFinishReason finishReason, String content) {
        return CompletionResponse.builder()
                .model("candidate-model:1.2.3")
                .usage(Usage.builder()
                        .promptTokens(11)
                        .completionTokens(7)
                        .totalTokens(18)
                        .precachedPromptTokens(3)
                        .build())
                .choices(List.of(Choice.builder()
                        .finishReason(finishReason)
                        .message(ChoiceMessage.builder()
                                .role(ChatMessageRole.ASSISTANT)
                                .content(content)
                                .build())
                        .build()))
                .build();
    }
}
