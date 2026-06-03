package com.github.denisspec989.strategy_launches_analyzer.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {
    @Bean
    @ConditionalOnProperty(name = "strategy-launches-analyzer.agent.provider", havingValue = "openai")
    @ConditionalOnBean(ChatClient.Builder.class)
    public AgentAnalyzer springAiAgentAnalyzer(ChatClient.Builder chatClientBuilder, AgentPromptBuilder promptBuilder) {
        return new SpringAiAgentAnalyzer(chatClientBuilder, promptBuilder);
    }

    @Bean
    @ConditionalOnMissingBean(AgentAnalyzer.class)
    public AgentAnalyzer fallbackAgentAnalyzer() {
        return new FallbackAgentAnalyzer();
    }
}
