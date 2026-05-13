package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgenticAdHocSubProcessJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.AgentOrchestrationConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.ToolResult;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.finos.fluxnova.bpm.engine.impl.context.Context;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.MessageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;

public class SubprocessToolCompletionListener implements ExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(SubprocessToolCompletionListener.class);

    private final AgentToolCatalogueRegistry toolCatalogueRegistry;

    public SubprocessToolCompletionListener(AgentToolCatalogueRegistry toolCatalogueRegistry) {
        this.toolCatalogueRegistry = toolCatalogueRegistry;
    }

    @Override
    public void notify(DelegateExecution execution) {
        String toolCallId = (String) execution.getVariableLocal("_agentToolCallId");
        if (toolCallId == null) {
            LOG.warn("Skipping tool result processing for execution '{}' - no toolCallId found", execution.getId())
            return;
        }

        ExecutionEntity execEntity = (ExecutionEntity) execution;
        ExecutionEntity scope = execEntity.getParent();
        String scopeExecutionId = scope.getId();

        Set<String> declaredWrites = toolCatalogueRegistry
                .resolve(execution.getProcessDefinitionId(), scope.getActivityId())
                .flatMap(cat -> cat.findById(execution.getCurrentActivityId()))
                .map(AgentToolEntry::writes)
                .orElse(Set.of());

        Map<String, Object> outputs = declaredWrites.stream()
                .filter(name -> scope.getVariable(name) != null)
                .collect(toMap(name -> name, scope::getVariable, (a, b) -> b));

        ToolResult result = new ToolResult(toolCallId, execution.getCurrentActivityId(), outputs, null);

        MessageEntity job = new MessageEntity();
        job.setExecution(scope);
        job.setJobHandlerType(AgenticAdHocSubProcessJobHandler.TYPE);
        job.setJobHandlerConfigurationRaw(
                AgentOrchestrationConfig.forToolCompletion(scopeExecutionId, result).toCanonicalString());

        Context.getCommandContext().getJobManager().insertAndHintJobExecutor(job);
    }
}
