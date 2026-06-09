package com.github.denisspec989.strategy_launches_analyzer.config;

import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentPromptBuilder;
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
    public AgentAnalyzer springAiAgentAnalyzer(
            ChatClient.Builder chatClientBuilder,
            AgentPromptBuilder promptBuilder,
            @Value("${spring.ai.openai.chat.model:not-configured}") String configuredModel
    ) {
        return new SpringAiAgentAnalyzer(chatClientBuilder.build(), promptBuilder, configuredModel);
    }
}
