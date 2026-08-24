package ru.sberbank.strategy_launches_analyzer.service.agent;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;

public interface AgentAnalyzer {
    AgentAnalysis analyze(AgentAnalysisInput input);
}
