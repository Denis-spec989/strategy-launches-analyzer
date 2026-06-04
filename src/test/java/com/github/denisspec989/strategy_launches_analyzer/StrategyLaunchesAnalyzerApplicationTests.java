package com.github.denisspec989.strategy_launches_analyzer;

import com.github.denisspec989.strategy_launches_analyzer.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.agent.FallbackAgentAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@ActiveProfiles("fallback")
class StrategyLaunchesAnalyzerApplicationTests {
	private final AgentAnalyzer agentAnalyzer;

	@Autowired
	StrategyLaunchesAnalyzerApplicationTests(AgentAnalyzer agentAnalyzer) {
		this.agentAnalyzer = agentAnalyzer;
	}

	@Test
	void contextLoadsWithFallbackAgentByDefault() {
		assertInstanceOf(FallbackAgentAnalyzer.class, agentAnalyzer);
	}

}
