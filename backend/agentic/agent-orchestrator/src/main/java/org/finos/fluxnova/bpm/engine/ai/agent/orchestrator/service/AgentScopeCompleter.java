package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service;

/**
 * Strategy for completing the agent scope execution.
 * 
 * <p>This interface allows different proposals (ad-hoc subprocess, event subprocess, etc.)
 * to define how they complete the scope when the agent loop finishes.
 */
public interface AgentScopeCompleter {

    void complete(String scopeExecutionId);
}
