package org.finos.fluxnova.bpm.engine.ai.agent.service;

import org.finos.fluxnova.bpm.engine.BadUserRequestException;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstanceWithVariables;
import org.finos.fluxnova.bpm.engine.shared.model.ToolCallRequest;
import org.finos.fluxnova.bpm.engine.shared.model.ToolInvocationResult;

public class AdHocActivityToolInvocationServiceImpl implements ToolInvocationService {

    RuntimeService runtimeService;

    public AdHocActivityToolInvocationServiceImpl(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public ToolInvocationResult invoke(String adHocSubprocessId, AgentToolCatalogue catalogue, ToolCallRequest request) {
        if (catalogue.findById(request.toolId()).isEmpty()) {
            return ToolInvocationResult.failure(
                    request.toolCallId(),
                    "Unknown tool: " + request.toolId()
            );
        }

        try {
            ProcessInstanceWithVariables processInstance = runtimeService.createProcessInstanceById(adHocSubprocessId)
                             .setVariableLocal("_agentToolCallId", request.toolCallId())
                             .executeWithVariablesInReturn();
            return ToolInvocationResult.success((String) processInstance.getVariables().get("_agentToolCallId"));
        } catch (BadUserRequestException e) {
            return ToolInvocationResult.failure(request.toolCallId(), e.getMessage());
        }
    }
}
