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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorEnginePluginTest {

    @Mock private AdHocAgentOrchestrationParseListener parseListener;
    @Mock private ProcessEngineConfigurationImpl processEngineConfiguration;

    @Nested
    class PreInit {

        @Test
        void addsParseListenerToExistingList() {
            List<BpmnParseListener> existingListeners = new ArrayList<>();
            when(processEngineConfiguration.getCustomPostBPMNParseListeners()).thenReturn(existingListeners);

            AgentOrchestratorEnginePlugin plugin = new AgentOrchestratorEnginePlugin(parseListener);
            plugin.preInit(processEngineConfiguration);

            assertEquals(1, existingListeners.size());
            assertSame(parseListener, existingListeners.get(0));
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
