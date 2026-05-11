package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service;

import org.finos.fluxnova.bpm.engine.RuntimeService;

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
