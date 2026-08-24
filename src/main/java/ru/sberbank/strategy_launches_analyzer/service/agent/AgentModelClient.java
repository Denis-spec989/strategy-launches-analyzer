package ru.sberbank.strategy_launches_analyzer.service.agent;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentModelCallResult;

public interface AgentModelClient {
    AgentModelCallResult call(AgentAnalysisInput input, AgentCallOptions options);
}
