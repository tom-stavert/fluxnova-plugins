package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgentOrchestrationJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.AgentOrchestrationConfig;
import org.finos.fluxnova.bpm.engine.impl.context.Context;
import org.finos.fluxnova.bpm.engine.impl.interceptor.CommandContext;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.JobManager;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.MessageEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubprocessToolCompletionListenerTest {

    private static final String PROC_DEF_ID = "procDef:1:abc";
    private static final String SCOPE_EXECUTION_ID = "scope-exec-001";
    private static final String ACTIVITY_ID = "creditScoreCheck";
    private static final String TOOL_CALL_ID = "tc-001";

    @Mock
    private AgentToolCatalogueRegistry toolCatalogueRegistry;

    @Mock
    private ExecutionEntity execution;

    @Mock
    private ExecutionEntity parentExecution;

    @Mock
    private CommandContext commandContext;

    @Mock
    private JobManager jobManager;

    private SubprocessToolCompletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new SubprocessToolCompletionListener(toolCatalogueRegistry);
    }

    private void stubToolCallExecution() {
        when(execution.getVariableLocal("_agentToolCallId")).thenReturn(TOOL_CALL_ID);
        when(execution.getProcessDefinitionId()).thenReturn(PROC_DEF_ID);
        when(execution.getCurrentActivityId()).thenReturn(ACTIVITY_ID);
        when(execution.getParent()).thenReturn(parentExecution);
        when(parentExecution.getId()).thenReturn(SCOPE_EXECUTION_ID);
        when(parentExecution.getActivityId()).thenReturn("agentSubprocess");
    }

    @Nested
    class NoToolCallId {

        @Test
        void notify_whenNoToolCallId_skips() {
            when(execution.getVariableLocal("_agentToolCallId")).thenReturn(null);

            listener.notify(execution);

            verifyNoInteractions(toolCatalogueRegistry);
        }
    }

    @Nested
    class OutputCapture {

        @Test
        void notify_capturesOutputsAndCreatesJob() {
            stubToolCallExecution();

            AgentToolEntry toolEntry = new AgentToolEntry(ACTIVITY_ID, "Credit Score Check",
                    "Checks credit score", Set.of("customerId"), Set.of("creditScore"));
            AgentToolCatalogue catalogue = new AgentToolCatalogue(PROC_DEF_ID, "agentSubprocess",
                    List.of(toolEntry));

            when(toolCatalogueRegistry.resolve(PROC_DEF_ID, "agentSubprocess"))
                    .thenReturn(Optional.of(catalogue));
            when(parentExecution.getVariable("creditScore")).thenReturn(750);
            when(commandContext.getJobManager()).thenReturn(jobManager);

            try (MockedStatic<Context> contextMock = mockStatic(Context.class)) {
                contextMock.when(Context::getCommandContext).thenReturn(commandContext);
                listener.notify(execution);
            }

            ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
            verify(jobManager).insertAndHintJobExecutor(captor.capture());

            MessageEntity job = captor.getValue();
            assertEquals(AgentOrchestrationJobHandler.TYPE, job.getJobHandlerType());

            AgentOrchestrationConfig config = AgentOrchestrationConfig.fromCanonicalString(
                    job.getJobHandlerConfigurationRaw());
            assertTrue(config.hasToolResult());
            assertEquals(TOOL_CALL_ID, config.toolResult().toolCallId());
            assertEquals(ACTIVITY_ID, config.toolResult().toolElementId());
            assertEquals(750, config.toolResult().outputs().get("creditScore"));
        }

        @Test
        void notify_whenToolNotInCatalogue_createsJobWithEmptyOutputs() {
            stubToolCallExecution();

            when(toolCatalogueRegistry.resolve(PROC_DEF_ID, "agentSubprocess"))
                    .thenReturn(Optional.empty());
            when(commandContext.getJobManager()).thenReturn(jobManager);

            try (MockedStatic<Context> contextMock = mockStatic(Context.class)) {
                contextMock.when(Context::getCommandContext).thenReturn(commandContext);
                listener.notify(execution);
            }

            ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
            verify(jobManager).insertAndHintJobExecutor(captor.capture());

            AgentOrchestrationConfig config = AgentOrchestrationConfig.fromCanonicalString(
                    captor.getValue().getJobHandlerConfigurationRaw());
            assertTrue(config.toolResult().outputs().isEmpty());
        }

        @Test
        void notify_whenDeclaredWriteIsNullInScope_isExcluded() {
            stubToolCallExecution();

            AgentToolEntry toolEntry = new AgentToolEntry(ACTIVITY_ID, "Credit Score Check",
                    "Checks credit score", Set.of(), Set.of("creditScore", "riskLevel"));
            AgentToolCatalogue catalogue = new AgentToolCatalogue(PROC_DEF_ID, "agentSubprocess",
                    List.of(toolEntry));

            when(toolCatalogueRegistry.resolve(PROC_DEF_ID, "agentSubprocess"))
                    .thenReturn(Optional.of(catalogue));
            when(parentExecution.getVariable("creditScore")).thenReturn(750);
            when(parentExecution.getVariable("riskLevel")).thenReturn(null);
            when(commandContext.getJobManager()).thenReturn(jobManager);

            try (MockedStatic<Context> contextMock = mockStatic(Context.class)) {
                contextMock.when(Context::getCommandContext).thenReturn(commandContext);
                listener.notify(execution);
            }

            ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
            verify(jobManager).insertAndHintJobExecutor(captor.capture());

            AgentOrchestrationConfig config = AgentOrchestrationConfig.fromCanonicalString(
                    captor.getValue().getJobHandlerConfigurationRaw());
            assertEquals(1, config.toolResult().outputs().size());
            assertEquals(750, config.toolResult().outputs().get("creditScore"));
        }
    }

    @Nested
    class JobAssociation {

        @Test
        void notify_jobIsAssociatedWithParentExecution() {
            stubToolCallExecution();

            when(toolCatalogueRegistry.resolve(PROC_DEF_ID, "agentSubprocess"))
                    .thenReturn(Optional.empty());
            when(commandContext.getJobManager()).thenReturn(jobManager);

            try (MockedStatic<Context> contextMock = mockStatic(Context.class)) {
                contextMock.when(Context::getCommandContext).thenReturn(commandContext);
                listener.notify(execution);
            }

            ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
            verify(jobManager).insertAndHintJobExecutor(captor.capture());

            assertEquals(parentExecution, captor.getValue().getExecution());
        }
    }
}
