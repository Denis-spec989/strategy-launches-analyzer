package com.github.denisspec989.strategy_launches_analyzer.config;

import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentModelClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentPromptBuilder;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.DefaultAgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.SpringAiAgentClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.SpringAiAgentAnalyzer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {
    @Bean
    @ConditionalOnProperty(name = "strategy-launches-analyzer.agent.provider", havingValue = "openai")
    public AgentModelClient springAiAgentClient(
            ChatClient.Builder chatClientBuilder,
            AgentPromptBuilder promptBuilder
    ) {
        return new SpringAiAgentClient(chatClientBuilder.build(), promptBuilder);
    }

    @Bean
    public AgentAnalysisPostProcessor agentAnalysisPostProcessor() {
        return new DefaultAgentAnalysisPostProcessor();
    }

    @Bean
    @ConditionalOnProperty(name = "strategy-launches-analyzer.agent.provider", havingValue = "openai")
    public AgentAnalyzer springAiAgentAnalyzer(
            AgentModelClient modelClient,
            AgentAnalysisPostProcessor postProcessor,
            @Value("${spring.ai.openai.chat.model:not-configured}") String configuredModel
    ) {
        return new SpringAiAgentAnalyzer(modelClient, postProcessor, configuredModel);
    }
}
