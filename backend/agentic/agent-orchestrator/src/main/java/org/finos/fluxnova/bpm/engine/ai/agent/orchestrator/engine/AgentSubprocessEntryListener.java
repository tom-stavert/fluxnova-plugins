package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgenticAdHocSubProcessJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model.AgentOrchestrationConfig;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.finos.fluxnova.bpm.engine.impl.context.Context;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.MessageEntity;

public class AgentSubprocessEntryListener implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) {
        String scopeExecutionId = execution.getId();

        MessageEntity job = new MessageEntity();
        job.setExecution((ExecutionEntity) execution);
        job.setJobHandlerType(AgenticAdHocSubProcessJobHandler.TYPE);
        job.setJobHandlerConfigurationRaw(
                AgentOrchestrationConfig.forEntry(scopeExecutionId).toCanonicalString());

        Context.getCommandContext().getJobManager().insertAndHintJobExecutor(job);
    }
}
