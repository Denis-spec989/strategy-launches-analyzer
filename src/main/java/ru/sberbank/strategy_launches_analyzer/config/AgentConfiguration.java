package ru.sberbank.strategy_launches_analyzer.config;

import ru.sberbank.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import ru.sberbank.strategy_launches_analyzer.service.agent.AgentAnalysisPostProcessor;
import ru.sberbank.strategy_launches_analyzer.service.agent.AgentModelClient;
import ru.sberbank.strategy_launches_analyzer.service.agent.AgentMetrics;
import ru.sberbank.strategy_launches_analyzer.service.agent.AgentPromptFingerprint;
import ru.sberbank.strategy_launches_analyzer.service.agent.AgentPromptBuilder;
import ru.sberbank.strategy_launches_analyzer.service.agent.DefaultAgentAnalyzer;
import ru.sberbank.strategy_launches_analyzer.service.agent.DefaultAgentAnalysisPostProcessor;
import ru.sberbank.strategy_launches_analyzer.service.agent.GigaChatAgentClient;
import ru.sberbank.strategy_launches_analyzer.service.agent.GigaChatStructuredCompletionClient;
import ru.sberbank.strategy_launches_analyzer.service.agent.MicrometerAgentMetrics;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import chat.giga.client.GigaChatClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {
    @Bean
    public GigaChatStructuredCompletionClient gigaChatStructuredCompletionClient(
            GigaChatClient gigaChatClient,
            ObjectMapper objectMapper
    ) {
        return new GigaChatStructuredCompletionClient(gigaChatClient, objectMapper);
    }

    @Bean
    public AgentModelClient gigaChatAgentClient(
            GigaChatStructuredCompletionClient completionClient,
            AgentPromptBuilder promptBuilder
    ) {
        return new GigaChatAgentClient(completionClient, promptBuilder);
    }

    @Bean
    public AgentAnalysisPostProcessor agentAnalysisPostProcessor() {
        return new DefaultAgentAnalysisPostProcessor();
    }

    @Bean
    public AgentMetrics agentMetrics(
            MeterRegistry meterRegistry,
            GigaChatProperties properties,
            ObjectMapper objectMapper,
            StrategyContractRegistry contractRegistry
    ) {
        String promptHash = AgentPromptFingerprint.normalPromptHash(objectMapper);
        String repairPromptHash = AgentPromptFingerprint.repairPromptHash(objectMapper);
        return new MicrometerAgentMetrics(
                meterRegistry,
                properties.model(),
                AgentPromptFingerprint.metricHash(promptHash),
                AgentPromptFingerprint.metricHash(repairPromptHash),
                contractRegistry.contracts()
        );
    }

    @Bean
    public AgentAnalyzer defaultAgentAnalyzer(
            AgentModelClient modelClient,
            AgentAnalysisPostProcessor postProcessor,
            GigaChatProperties properties,
            AgentMetrics agentMetrics,
            @Value("${strategy-launches-analyzer.agent.repair-enabled:true}") boolean repairEnabled
    ) {
        return new DefaultAgentAnalyzer(
                modelClient,
                postProcessor,
                properties.model(),
                repairEnabled,
                agentMetrics
        );
    }
}
