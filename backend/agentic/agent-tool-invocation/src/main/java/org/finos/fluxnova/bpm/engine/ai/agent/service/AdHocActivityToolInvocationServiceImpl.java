package org.finos.fluxnova.bpm.engine.ai.agent.service;

import org.finos.fluxnova.bpm.engine.BadUserRequestException;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.model.ToolCallRequest;
import org.finos.fluxnova.bpm.engine.ai.agent.model.ToolInvocationResult;
import org.springframework.stereotype.Service;

@Service
public class AdHocActivityToolInvocationServiceImpl implements ToolInvocationService {

    RuntimeServiceToolRunIdWrapper runtimeService;

    public AdHocActivityToolInvocationServiceImpl(RuntimeService runtimeService) {
        this.runtimeService = new RuntimeServiceToolRunIdWrapper(runtimeService);
    }

    @Override
    public ToolInvocationResult invoke(String scopeExecutionId, AgentToolCatalogue catalogue, ToolCallRequest request) {
        if (catalogue.findById(request.toolId()).isEmpty()) {
            return ToolInvocationResult.failure(
                    request.toolCallId(),
                    "Unknown tool: " + request.toolId()
            );
        }

        try {
            runtimeService.triggerAdHocWithCorrelationId(scopeExecutionId, request.toolId(), request.toolCallId());
            return ToolInvocationResult.success(request.toolCallId());
        } catch (BadUserRequestException e) {
            return ToolInvocationResult.failure(request.toolCallId(), e.getMessage());
        }
    }
}
