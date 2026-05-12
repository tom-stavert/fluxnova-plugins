package org.finos.fluxnova.bpm.engine.ai.agent.service;

import org.finos.fluxnova.bpm.engine.BadUserRequestException;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.helper.MockProvider;
import org.finos.fluxnova.bpm.engine.ai.agent.model.ToolCallRequest;
import org.finos.fluxnova.bpm.engine.ai.agent.model.ToolInvocationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdHocActivityToolInvocationServiceImplTest {

    @Test
    void invoke_withValidToolId_returnsSuccessAndCallsTool() {
        String toolId = "toolId";

        String scopeExecutionId = "scopeExecutionId";

        String processExecutionId = "processExecutionId";
        List<AgentToolEntry> agentToolEntries = List.of(
                new AgentToolEntry(toolId, "toolName", "toolDescription", Set.of("reads"), Set.of("writes"))
        );
        AgentToolCatalogue catalogue = new AgentToolCatalogue(processExecutionId, toolId, agentToolEntries);

        String toolCallId = "toolCallId";
        ToolCallRequest request = new ToolCallRequest(toolCallId, toolId);


        RuntimeService runtimeService = MockProvider.createMockRuntimeService();
        ToolInvocationService toolInvocationService = new AdHocActivityToolInvocationServiceImpl(runtimeService);

        ToolInvocationResult expectedResult = ToolInvocationResult.success(toolCallId);

        ToolInvocationResult result = toolInvocationService.invoke(scopeExecutionId, catalogue, request);

        assertEquals(expectedResult, result);
        verify(runtimeService, atLeastOnce()).createProcessInstanceById(scopeExecutionId);
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
        verify(runtimeService, times(0)).startProcessInstanceById(toolId);
    }

    @Test
    void invoke_withToolIdNotInCatalogue_returnsFailure() {
        String toolId = "toolId";

        String scopeExecutionId = "scopeExecutionId";

        String processExecutionId = "processExecutionId";
        List<AgentToolEntry> agentToolEntries = List.of(
                new AgentToolEntry("toolId2", "toolName2", "toolDescription2", Set.of("reads2"), Set.of("writes2")),
                new AgentToolEntry("toolId3", "toolName3", "toolDescription3", Set.of("reads3"), Set.of("writes3"))
        );
        AgentToolCatalogue catalogue = new AgentToolCatalogue(processExecutionId, toolId, agentToolEntries);

        String toolCallId = "toolCallId";
        ToolCallRequest request = new ToolCallRequest(toolCallId, toolId);

        RuntimeService runtimeService = mock(RuntimeService.class);
        ToolInvocationService toolInvocationService = new AdHocActivityToolInvocationServiceImpl(runtimeService);

        ToolInvocationResult expectedResult = ToolInvocationResult.failure(toolCallId, "Unknown tool: " + toolId);


        ToolInvocationResult result = toolInvocationService.invoke(scopeExecutionId, catalogue, request);


        assertEquals(expectedResult, result);
        verify(runtimeService, times(0)).startProcessInstanceById(toolId);
    }

    @Test
    void invoke_withBadUserRequestException_returnsFailure() {
        String toolId = "toolId";

        String scopeExecutionId = "scopeExecutionId";

        String processExecutionId = "processExecutionId";
        List<AgentToolEntry> agentToolEntries = List.of(
                new AgentToolEntry(toolId, "toolName", "toolDescription", Set.of("reads"), Set.of("writes"))
        );
        AgentToolCatalogue catalogue = new AgentToolCatalogue(processExecutionId, toolId, agentToolEntries);

        String toolCallId = "toolCallId";
        ToolCallRequest request = new ToolCallRequest(toolCallId, toolId);


        RuntimeService runtimeService = MockProvider.createMockRuntimeService();
        String errorMessage = "Bad user request exception";
        when(runtimeService.createProcessInstanceById(any())).thenThrow(new BadUserRequestException(errorMessage));
        ToolInvocationService toolInvocationService = new AdHocActivityToolInvocationServiceImpl(runtimeService);

        ToolInvocationResult expectedResult = ToolInvocationResult.failure(toolCallId, errorMessage);

        ToolInvocationResult result = toolInvocationService.invoke(scopeExecutionId, catalogue, request);

        assertEquals(expectedResult, result);
        verify(runtimeService, atLeastOnce()).createProcessInstanceById(scopeExecutionId);
    }
}
