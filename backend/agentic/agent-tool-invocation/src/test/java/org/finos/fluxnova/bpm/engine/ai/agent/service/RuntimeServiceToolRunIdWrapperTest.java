package org.finos.fluxnova.bpm.engine.ai.agent.service;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.helper.MockProvider;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstantiationBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RuntimeServiceToolRunIdWrapperTest {

    @Test
    void triggerAdHocWithCorrelationId_withIds_returnsProcessInstanceWithAgentToolCallIdSet() {
        ProcessInstantiationBuilder builder = MockProvider.createMockInstantiationBuilder();
        RuntimeService runtimeService = MockProvider.createMockRuntimeService(builder);

        String scopeExecutionId = MockProvider.EXAMPLE_EXECUTION_ID;
        String toolId = "toolId";
        String toolCallId = "toolCallId";

        RuntimeServiceToolRunIdWrapper runtimeServiceToolRunIdWrapper = new RuntimeServiceToolRunIdWrapper(runtimeService);

        ProcessInstance result = runtimeServiceToolRunIdWrapper.triggerAdHocWithCorrelationId(scopeExecutionId, toolId, toolCallId);

        assertEquals(MockProvider.EXAMPLE_TENANT_ID, result.getTenantId());
        assertEquals(MockProvider.EXAMPLE_PROCESS_INSTANCE_IS_ENDED, result.isEnded());

        verify(builder, times(1)).setVariableLocal("_agentToolCallId", toolCallId);
    }
}