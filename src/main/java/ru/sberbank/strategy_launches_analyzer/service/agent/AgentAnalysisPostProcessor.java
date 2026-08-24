package ru.sberbank.strategy_launches_analyzer.service.agent;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentPostProcessingResult;
import ru.sberbank.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import ru.sberbank.strategy_launches_analyzer.dto.agent.TokenUsage;

public interface AgentAnalysisPostProcessor {
    AgentPostProcessingResult process(
            StructuredAgentAnalysis raw,
            AgentAnalysisInput input,
            TokenUsage tokenUsage
    );
}
