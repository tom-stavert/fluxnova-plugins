package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.finos.fluxnova.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.finos.fluxnova.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

import java.util.ArrayList;
import java.util.List;

public class AgentOrchestratorEnginePlugin extends AbstractProcessEnginePlugin {

    private final AgentOrchestrationParseListener parseListener;

    public AgentOrchestratorEnginePlugin(AgentOrchestrationParseListener parseListener) {
        this.parseListener = parseListener;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        List<BpmnParseListener> listeners = processEngineConfiguration.getCustomPostBPMNParseListeners();
        if (listeners == null) {
            listeners = new ArrayList<>();
            processEngineConfiguration.setCustomPostBPMNParseListeners(listeners);
        }
        listeners.add(parseListener);
    }
}
