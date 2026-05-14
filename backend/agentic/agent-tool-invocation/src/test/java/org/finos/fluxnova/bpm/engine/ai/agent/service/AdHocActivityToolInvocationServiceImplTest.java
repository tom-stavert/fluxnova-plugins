package org.finos.fluxnova.bpm.engine.ai.agent.service;

import org.finos.fluxnova.bpm.engine.BadUserRequestException;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstantiationBuilder;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstanceWithVariables;
import org.finos.fluxnova.bpm.engine.shared.model.ToolCallRequest;
import org.finos.fluxnova.bpm.engine.shared.model.ToolInvocationResult;
import org.finos.fluxnova.bpm.engine.variable.impl.VariableMapImpl;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdHocActivityToolInvocationServiceImplTest {

    @Test
    void invoke_withValidToolId_returnsSuccessAndCallsTool() {
        String toolId = "toolId";

        String scopeExecutionId = "scopeExecutionId";

        String processExecutionId = "processExecutionId";
        List<AgentToolEntry> agentToolEntries = List.of(
                new AgentToolEntry(toolId, "toolName", "toolDescription", Set.of("reads"), Set.of("writes")));
        AgentToolCatalogue catalogue = new AgentToolCatalogue(processExecutionId, toolId, agentToolEntries);

        String toolCallId = "toolCallId";
        ToolCallRequest request = new ToolCallRequest(toolCallId, toolId);

        Map<String, Object> variables = new HashMap<>();
        RuntimeService runtimeService = mock(RuntimeService.class);
        ProcessInstantiationBuilder builder = mock(ProcessInstantiationBuilder.class);
        ProcessInstanceWithVariables processInstanceWithVariables = mock(ProcessInstanceWithVariables.class);

        when(runtimeService.createProcessInstanceById(any())).thenReturn(builder);
        when(builder.setVariableLocal(any(), any())).thenAnswer(invocation -> {
            variables.put(invocation.getArgument(0), invocation.getArgument(1));
            return builder;
        });
        when(builder.executeWithVariablesInReturn()).thenReturn(processInstanceWithVariables);
        when(processInstanceWithVariables.getVariables()).thenAnswer(invocation -> new VariableMapImpl(variables));
        when(processInstanceWithVariables.isEnded()).thenReturn(false);

        ToolInvocationService toolInvocationService = new AdHocActivityToolInvocationServiceImpl(runtimeService);

        ToolInvocationResult expectedResult = ToolInvocationResult.success(toolCallId);

        ToolInvocationResult result = toolInvocationService.invoke(scopeExecutionId, catalogue, request);

        assertEquals(expectedResult, result);
    }

    @Test
    void invoke_withEmptyCatalogueTools_returnsFailure() {
        String toolId = "toolId";

        String scopeExecutionId = "scopeExecutionId";

        String processExecutionId = "processExecutionId";
        List<AgentToolEntry> agentToolEntries = List.of();
        AgentToolCatalogue catalogue = new AgentToolCatalogue(processExecutionId, toolId, agentToolEntries);

        String toolCallId = "toolCallId";
        ToolCallRequest request = new ToolCallRequest(toolCallId, toolId);

        RuntimeService runtimeService = mock(RuntimeService.class);
        ToolInvocationService toolInvocationService = new AdHocActivityToolInvocationServiceImpl(runtimeService);

        ToolInvocationResult expectedResult = ToolInvocationResult.failure(toolCallId, "Unknown tool: " + toolId);

        ToolInvocationResult result = toolInvocationService.invoke(scopeExecutionId, catalogue, request);

        assertEquals(expectedResult, result);
    }

    @Test
    void invoke_withToolIdNotInCatalogue_returnsFailure() {
        String toolId = "toolId";

        String scopeExecutionId = "scopeExecutionId";

        String processExecutionId = "processExecutionId";
        List<AgentToolEntry> agentToolEntries = List.of(
                new AgentToolEntry("toolId2", "toolName2", "toolDescription2", Set.of("reads2"), Set.of("writes2")),
                new AgentToolEntry("toolId3", "toolName3", "toolDescription3", Set.of("reads3"), Set.of("writes3")));
        AgentToolCatalogue catalogue = new AgentToolCatalogue(processExecutionId, toolId, agentToolEntries);

        String toolCallId = "toolCallId";
        ToolCallRequest request = new ToolCallRequest(toolCallId, toolId);

        RuntimeService runtimeService = mock(RuntimeService.class);
        ToolInvocationService toolInvocationService = new AdHocActivityToolInvocationServiceImpl(runtimeService);

        ToolInvocationResult expectedResult = ToolInvocationResult.failure(toolCallId, "Unknown tool: " + toolId);

        ToolInvocationResult result = toolInvocationService.invoke(scopeExecutionId, catalogue, request);

        assertEquals(expectedResult, result);
    }

    @Test
    void invoke_withBadUserRequestException_returnsFailure() {
        String toolId = "toolId";

        String scopeExecutionId = "scopeExecutionId";

        String processExecutionId = "processExecutionId";
        List<AgentToolEntry> agentToolEntries = List.of(
                new AgentToolEntry(toolId, "toolName", "toolDescription", Set.of("reads"), Set.of("writes")));
        AgentToolCatalogue catalogue = new AgentToolCatalogue(processExecutionId, toolId, agentToolEntries);

        String toolCallId = "toolCallId";
        ToolCallRequest request = new ToolCallRequest(toolCallId, toolId);

        RuntimeService runtimeService = mock(RuntimeService.class);
        String errorMessage = "Bad user request exception";
        when(runtimeService.createProcessInstanceById(any())).thenThrow(new BadUserRequestException(errorMessage));
        ToolInvocationService toolInvocationService = new AdHocActivityToolInvocationServiceImpl(runtimeService);

        ToolInvocationResult expectedResult = ToolInvocationResult.failure(toolCallId, errorMessage);

        ToolInvocationResult result = toolInvocationService.invoke(scopeExecutionId, catalogue, request);

        assertEquals(expectedResult, result);
    }
}
