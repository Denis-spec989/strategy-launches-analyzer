package com.github.denisspec989.strategy_launches_analyzer;

import com.github.denisspec989.strategy_launches_analyzer.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.agent.SpringAiAgentAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = "OPENAI_API_KEY=dummy-test-key")
@ActiveProfiles("openai")
class OpenAiProfileConfigurationTest {
    private final AgentAnalyzer agentAnalyzer;

    @Autowired
    OpenAiProfileConfigurationTest(AgentAnalyzer agentAnalyzer) {
        this.agentAnalyzer = agentAnalyzer;
    }

    @Test
    void contextLoadsWithSpringAiAgent() {
        assertInstanceOf(SpringAiAgentAnalyzer.class, agentAnalyzer);
    }
}
