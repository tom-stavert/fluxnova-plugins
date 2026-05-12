package org.finos.fluxnova.bpm.engine.ai.agent.helper;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstantiationBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockProvider {// tenant ids
    public static final String EXAMPLE_TENANT_ID = "aTenantId";

    // process instance
    public static final String EXAMPLE_PROCESS_INSTANCE_BUSINESS_KEY = "aKey";
    public static final String EXAMPLE_PROCESS_INSTANCE_ID = "aProcInstId";
    public static final boolean EXAMPLE_PROCESS_INSTANCE_IS_SUSPENDED = false;
    public static final boolean EXAMPLE_PROCESS_INSTANCE_IS_ENDED = false;

    // execution
    public static final String EXAMPLE_EXECUTION_ID = "anExecutionId";

    // process definition
    public static final String EXAMPLE_PROCESS_DEFINITION_ID = "aProcDefId";
    public static final String EXAMPLE_PROCESS_DEFINITION_KEY = "aKey";

    // case instance
    public static final String EXAMPLE_CASE_INSTANCE_ID = "aCaseInstId";

    public static RuntimeService createMockRuntimeService() { return createMockRuntimeService(createMockInstantiationBuilder()); }

    public static RuntimeService createMockRuntimeService(ProcessInstantiationBuilder builder) {
        RuntimeService runtimeService = mock(RuntimeService.class);
        when(runtimeService.createProcessInstanceById(any())).thenReturn(builder);
        return runtimeService;
    }

    public static ProcessInstantiationBuilder createMockInstantiationBuilder() {
        ProcessInstantiationBuilder instantiationBuilder = mock(ProcessInstantiationBuilder.class);
        ProcessInstance processInstance = createMockInstance();
        when(instantiationBuilder.execute()).thenReturn(processInstance);
        return instantiationBuilder;
    }

    public static ProcessInstance createMockInstance() {
        return createMockInstance(EXAMPLE_TENANT_ID);
    }

    public static ProcessInstance createMockInstance(String tenantId) {
        ProcessInstance mock = mock(ProcessInstance.class);

        when(mock.getId()).thenReturn(EXAMPLE_PROCESS_INSTANCE_ID);
        when(mock.getBusinessKey()).thenReturn(EXAMPLE_PROCESS_INSTANCE_BUSINESS_KEY);
        when(mock.getCaseInstanceId()).thenReturn(EXAMPLE_CASE_INSTANCE_ID);
        when(mock.getProcessDefinitionId()).thenReturn(EXAMPLE_PROCESS_DEFINITION_ID);
        when(mock.getProcessDefinitionKey()).thenReturn(EXAMPLE_PROCESS_DEFINITION_KEY);
        when(mock.getProcessInstanceId()).thenReturn(EXAMPLE_PROCESS_INSTANCE_ID);
        when(mock.isSuspended()).thenReturn(EXAMPLE_PROCESS_INSTANCE_IS_SUSPENDED);
        when(mock.isEnded()).thenReturn(EXAMPLE_PROCESS_INSTANCE_IS_ENDED);
        when(mock.getTenantId()).thenReturn(tenantId);

        return mock;
    }
}
