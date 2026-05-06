package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgentOrchestrationJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.AgentOrchestrationConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.ToolResult;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.finos.fluxnova.bpm.engine.impl.context.Context;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.MessageEntity;

import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;

public class ToolCompletionListener implements ExecutionListener {

    private final AgentToolCatalogueRegistry toolCatalogueRegistry;

    public ToolCompletionListener(AgentToolCatalogueRegistry toolCatalogueRegistry) {
        this.toolCatalogueRegistry = toolCatalogueRegistry;
    }

    @Override
    public void notify(DelegateExecution execution) {
        String toolCallId = (String) execution.getVariableLocal("_agentToolCallId");
        if (toolCallId == null) {
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
        job.setJobHandlerType(AgentOrchestrationJobHandler.TYPE);
        job.setJobHandlerConfigurationRaw(
                AgentOrchestrationConfig.forToolCompletion(scopeExecutionId, result).toCanonicalString());

        Context.getCommandContext().getJobManager().insertAndHintJobExecutor(job);
    }
}
