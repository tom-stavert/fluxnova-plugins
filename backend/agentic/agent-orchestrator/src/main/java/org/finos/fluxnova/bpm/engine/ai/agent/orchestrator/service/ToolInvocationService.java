package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.shared.agent.model.ToolCallRequest;
import org.finos.fluxnova.bpm.engine.shared.agent.model.ToolInvocationResult;

/**
 * Component 4 interface — tool invocation service.
 *
 * <p>Called by the orchestrator to trigger a single BPMN tool activity inside
 * an agentic ad-hoc subprocess. The implementation fires the activity via the
 * engine's ad-hoc trigger API with a correlation ID for result tracking.
 */
public interface ToolInvocationService {

    ToolInvocationResult invoke(String scopeExecutionId,
                                AgentToolCatalogue catalogue,
                                ToolCallRequest request);
}
