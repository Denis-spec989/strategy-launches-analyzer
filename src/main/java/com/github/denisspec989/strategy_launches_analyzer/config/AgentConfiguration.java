package com.github.denisspec989.strategy_launches_analyzer.config;

import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentModelClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentPromptBuilder;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.DefaultAgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.DefaultAgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatAgentClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatStructuredCompletionClient;
import chat.giga.client.GigaChatClient;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    public AgentAnalyzer defaultAgentAnalyzer(
            AgentModelClient modelClient,
            AgentAnalysisPostProcessor postProcessor,
            GigaChatProperties properties
    ) {
        return new DefaultAgentAnalyzer(modelClient, postProcessor, properties.model());
    }
}
