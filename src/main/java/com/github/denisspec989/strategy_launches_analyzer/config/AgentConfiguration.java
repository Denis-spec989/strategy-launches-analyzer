package com.github.denisspec989.strategy_launches_analyzer.config;

import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentModelClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentMetrics;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentPromptFingerprint;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentPromptBuilder;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.DefaultAgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.DefaultAgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatAgentClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatStructuredCompletionClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.MicrometerAgentMetrics;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
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
