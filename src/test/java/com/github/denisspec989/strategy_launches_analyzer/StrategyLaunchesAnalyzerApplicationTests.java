package com.github.denisspec989.strategy_launches_analyzer;

import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.SpringAiAgentAnalyzer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = "OPENAI_API_KEY=dummy-test-key")
@ActiveProfiles("openai")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Autowired)
class StrategyLaunchesAnalyzerApplicationTests {
	private final AgentAnalyzer agentAnalyzer;

	@Test
	void contextLoadsWithSpringAiAgent() {
		assertInstanceOf(SpringAiAgentAnalyzer.class, agentAnalyzer);
	}

}
