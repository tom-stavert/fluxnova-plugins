package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service;

import org.finos.fluxnova.bpm.engine.RuntimeService;

/**
 * Completion strategy for Proposal 3: Agent Ad-Hoc Subprocess.
 * 
 * <p>Completes the ad-hoc subprocess scope using the engine's ad-hoc completion mechanism.
 */
public class AdHocSubprocessCompleter implements AgentScopeCompleter {

    private final RuntimeService runtimeService;

    public AdHocSubprocessCompleter(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public void complete(String scopeExecutionId) {
        runtimeService.completeAdHocSubprocess(scopeExecutionId);
    }
}
