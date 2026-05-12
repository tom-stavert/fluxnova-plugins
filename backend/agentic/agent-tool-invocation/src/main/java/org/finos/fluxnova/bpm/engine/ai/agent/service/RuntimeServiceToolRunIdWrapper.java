package org.finos.fluxnova.bpm.engine.ai.agent.service;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.runtime.*;

public class RuntimeServiceToolRunIdWrapper {

    RuntimeService runtimeService;

    RuntimeServiceToolRunIdWrapper(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    ProcessInstance triggerAdHocWithCorrelationId(
            String scopeExecutionId,
            String toolId,
            String toolCallId
    ) {
        ProcessInstantiationBuilder builder = runtimeService.createProcessInstanceById(scopeExecutionId);
        builder.setVariableLocal("_agentToolCallId", toolCallId);
        return builder.execute();
    }
}
