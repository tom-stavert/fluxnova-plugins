package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.finos.fluxnova.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorEnginePluginTest {

    @Mock private AdHocAgentOrchestrationParseListener parseListener;
    @Mock private ProcessEngineConfigurationImpl processEngineConfiguration;

    @Nested
    class PreInit {

        @Test
        void addsParseListenerToExistingList() {
            BpmnParseListener existingListener = mock(BpmnParseListener.class);
            when(processEngineConfiguration.getCustomPostBPMNParseListeners()).thenReturn(new ArrayList<>(List.of(existingListener)));

            AgentOrchestratorEnginePlugin plugin = new AgentOrchestratorEnginePlugin(parseListener);
            plugin.preInit(processEngineConfiguration);

            verify(processEngineConfiguration).setCustomPostBPMNParseListeners(argThat(list ->
                    list.size() == 2 && list.get(0) == existingListener && list.get(1) == parseListener));
        }

        @Test
        void createsListWhenNull() {
            when(processEngineConfiguration.getCustomPostBPMNParseListeners()).thenReturn(null);

            AgentOrchestratorEnginePlugin plugin = new AgentOrchestratorEnginePlugin(parseListener);
            plugin.preInit(processEngineConfiguration);

            verify(processEngineConfiguration).setCustomPostBPMNParseListeners(argThat(list ->
                    list.size() == 1 && list.get(0) == parseListener));
        }
    }
}
