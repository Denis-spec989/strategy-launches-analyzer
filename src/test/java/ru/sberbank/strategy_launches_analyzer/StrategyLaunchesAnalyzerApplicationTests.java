package ru.sberbank.strategy_launches_analyzer;

import ru.sberbank.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import ru.sberbank.strategy_launches_analyzer.service.agent.DefaultAgentAnalyzer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = {
        "strategy-launches-analyzer.agent.gigachat.auth-mode=user-password",
        "strategy-launches-analyzer.agent.gigachat.model=test-model",
        "strategy-launches-analyzer.agent.gigachat.user-password.api-url=https://api.example/v1",
        "strategy-launches-analyzer.agent.gigachat.user-password.auth-api-url=https://auth.example/v1",
        "strategy-launches-analyzer.agent.gigachat.user-password.username=test-user",
        "strategy-launches-analyzer.agent.gigachat.user-password.password=test-password",
        "strategy-launches-analyzer.agent.gigachat.user-password.scope=GIGACHAT_API_PERS"
})
@RequiredArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @Autowired)
class StrategyLaunchesAnalyzerApplicationTests {
	private final AgentAnalyzer agentAnalyzer;

	@Test
	void contextLoadsWithDefaultAgent() {
		assertInstanceOf(DefaultAgentAnalyzer.class, agentAnalyzer);
	}

}
