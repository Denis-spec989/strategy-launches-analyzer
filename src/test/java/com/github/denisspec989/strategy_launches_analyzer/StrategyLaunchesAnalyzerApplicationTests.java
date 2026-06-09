package com.github.denisspec989.strategy_launches_analyzer;

import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.FallbackAgentAnalyzer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@ActiveProfiles("fallback")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Autowired)
class StrategyLaunchesAnalyzerApplicationTests {
	private final AgentAnalyzer agentAnalyzer;

	@Test
	void contextLoadsWithFallbackAgentByDefault() {
		assertInstanceOf(FallbackAgentAnalyzer.class, agentAnalyzer);
	}

}
