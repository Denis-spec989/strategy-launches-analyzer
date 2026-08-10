package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;

public interface AgentModelClient {
    AgentModelCallResult call(AgentAnalysisInput input, AgentCallOptions options);
}
