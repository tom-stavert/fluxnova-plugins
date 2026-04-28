package org.finos.fluxnova.bpm.engine.ai.agent.registry;

import org.finos.fluxnova.bpm.engine.ProcessEngineException;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.ai.agent.extract.AgentConfigExtractor;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentConfigRegistryTest {

    private static final String PROC_DEF_ID = "creditCheck:1:abc";
    private static final String ELEMENT_ID = "creditCheckAgent";

    private static final String BPMN_WITH_AGENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
              <process id="creditCheck">
                <adHocSubProcess id="creditCheckAgent">
                  <extensionElements>
                    <agent:config provider="ollama"
                                  model="llama3.1"
                                  systemPrompt="You are a credit analyst."/>
                  </extensionElements>
                </adHocSubProcess>
              </process>
            </definitions>
            """;

    private static final String BPMN_WITHOUT_AGENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="plain">
                <adHocSubProcess id="plainSub"/>
              </process>
            </definitions>
            """;

    @Mock
    private RepositoryService repositoryService;

    private AgentConfigRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentConfigRegistry(repositoryService, new AgentConfigExtractor());
    }

    @Test
    void resolve_whenProcessHasNoAgentConfig_returnsEmpty() throws Exception {
        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenReturn(new ByteArrayInputStream(BPMN_WITHOUT_AGENT.getBytes(StandardCharsets.UTF_8)));

        Optional<AgentConfig> result = registry.resolve(PROC_DEF_ID, ELEMENT_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_whenProcessHasAgentConfig_returnsConfig() throws Exception {
        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenReturn(new ByteArrayInputStream(BPMN_WITH_AGENT.getBytes(StandardCharsets.UTF_8)));

        Optional<AgentConfig> result = registry.resolve(PROC_DEF_ID, ELEMENT_ID);

        assertTrue(result.isPresent());
        AgentConfig config = result.get();
        assertEquals(PROC_DEF_ID, config.processDefinitionId());
        assertEquals(ELEMENT_ID, config.elementId());
        assertEquals("ollama", config.provider());
        assertEquals("llama3.1", config.model());
                assertEquals(ELEMENT_ID, config.toolScopeElementId());
    }

    @Test
    void resolve_calledTwiceForSameDefinition_scansOnlyOnce() throws Exception {
        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenReturn(new ByteArrayInputStream(BPMN_WITH_AGENT.getBytes(StandardCharsets.UTF_8)));

        registry.resolve(PROC_DEF_ID, ELEMENT_ID);
        registry.resolve(PROC_DEF_ID, ELEMENT_ID);

        verify(repositoryService, times(1)).getProcessModel(PROC_DEF_ID);
    }

    @Test
    void unregisterAll_clearsAllState() throws Exception {
        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenReturn(new ByteArrayInputStream(BPMN_WITH_AGENT.getBytes(StandardCharsets.UTF_8)));

        registry.resolve(PROC_DEF_ID, ELEMENT_ID);
        registry.unregisterAll();

        // registry has been cleared — resolve returns empty without a second scan source
        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenReturn(new ByteArrayInputStream(BPMN_WITHOUT_AGENT.getBytes(StandardCharsets.UTF_8)));

        Optional<AgentConfig> result = registry.resolve(PROC_DEF_ID, ELEMENT_ID);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_afterUnregisterAll_rescansDefinition() throws Exception {
        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenReturn(new ByteArrayInputStream(BPMN_WITH_AGENT.getBytes(StandardCharsets.UTF_8)));
        registry.resolve(PROC_DEF_ID, ELEMENT_ID);

        registry.unregisterAll();

        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenReturn(new ByteArrayInputStream(BPMN_WITH_AGENT.getBytes(StandardCharsets.UTF_8)));
        registry.resolve(PROC_DEF_ID, ELEMENT_ID);

        verify(repositoryService, times(2)).getProcessModel(PROC_DEF_ID);
    }

    @Test
    void resolve_whenScanThrows_doesNotMarkAsPermanentlyScanned() throws Exception {
        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenThrow(new RuntimeException("DB blip"))
                .thenReturn(new ByteArrayInputStream(BPMN_WITH_AGENT.getBytes(StandardCharsets.UTF_8)));

        // First call fails — exception propagates
        assertThrows(RuntimeException.class, () -> registry.resolve(PROC_DEF_ID, ELEMENT_ID));

        // Second call should retry and succeed (not permanently marked as scanned)
        Optional<AgentConfig> second = registry.resolve(PROC_DEF_ID, ELEMENT_ID);
        assertTrue(second.isPresent());
        assertEquals("ollama", second.get().provider());

        verify(repositoryService, times(2)).getProcessModel(PROC_DEF_ID);
    }

    @Test
        void resolve_whenScanThrowsProcessEngineException_retriesWithoutPropagating() throws Exception {
                when(repositoryService.getProcessModel(PROC_DEF_ID))
                                .thenThrow(new ProcessEngineException("parse failure"))
                                .thenReturn(new ByteArrayInputStream(BPMN_WITH_AGENT.getBytes(StandardCharsets.UTF_8)));

                Optional<AgentConfig> first = registry.resolve(PROC_DEF_ID, ELEMENT_ID);
                assertTrue(first.isEmpty());

                Optional<AgentConfig> second = registry.resolve(PROC_DEF_ID, ELEMENT_ID);
                assertTrue(second.isPresent());
                assertEquals("ollama", second.get().provider());

                verify(repositoryService, times(2)).getProcessModel(PROC_DEF_ID);
        }

        @Test
        void resolve_whenScanThrowsUnchecked_propagatesException() throws Exception {
        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenThrow(new RuntimeException("unexpected"));

        assertThrows(RuntimeException.class, () -> registry.resolve(PROC_DEF_ID, ELEMENT_ID));
    }

    @Test
    void resolve_whenStreamCloseThrowsIOException_returnsConfigButDoesNotCacheScan() throws Exception {
        InputStream brokenCloseStream = new ByteArrayInputStream(
                BPMN_WITH_AGENT.getBytes(StandardCharsets.UTF_8)) {
            private boolean closedOnce = false;

            @Override
            public void close() throws IOException {
                if (closedOnce) {
                    throw new IOException("stream close failed");
                }
                closedOnce = true;
            }
        };

        when(repositoryService.getProcessModel(PROC_DEF_ID))
                .thenReturn(brokenCloseStream)
                .thenReturn(new ByteArrayInputStream(BPMN_WITH_AGENT.getBytes(StandardCharsets.UTF_8)));

        // First call — extraction succeeds before close() throws;
        // IOException is caught so resolve does NOT throw, and configs are already stored
        Optional<AgentConfig> first = registry.resolve(PROC_DEF_ID, ELEMENT_ID);
        assertTrue(first.isPresent());

        // Second call — re-scans because doScan returned null (not cached in scanned map)
        Optional<AgentConfig> second = registry.resolve(PROC_DEF_ID, ELEMENT_ID);
        assertTrue(second.isPresent());

        // getProcessModel was called twice because the IOException prevented caching
        verify(repositoryService, times(2)).getProcessModel(PROC_DEF_ID);
    }
}
