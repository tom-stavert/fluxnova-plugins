package org.finos.fluxnova.bpm.engine.ai.agent.service;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.model.ToolCallRequest;
import org.finos.fluxnova.bpm.engine.ai.agent.model.ToolInvocationResult;

public interface ToolInvocationService {
    ToolInvocationResult invoke(String scopeExecutionId, AgentToolCatalogue catalogue, ToolCallRequest request);
}
