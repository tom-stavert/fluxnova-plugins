package org.finos.fluxnova.bpm.engine.ai.agent.integration;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.autoconfigure.AgentConfigAutoConfiguration;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.autoconfigure.AgentDiscoveryAutoConfiguration;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the external tool-scope feature, where an agent's
 * {@code agent:config} carries a {@code toolScopeElementId} attribute that
 * points to a sibling BPMN element rather than the agent subprocess itself.
 *
 * <p>The unit tests for {@code AgentUtilityRegistry} cover the case where the
 * referenced element is absent, but they do so with a mock builder and therefore
 * cannot verify that the <em>correct</em> element is passed to
 * {@code AdHocSubProcessCatalogueBuilder}. These tests exercise the full
 * registry → BPMN-scan → builder pipeline to close that gap.
 */
class AgentExternalToolScopeIT {

    private static final String PROC_DEF_ID = "externalScope:1:abc";
    private static final String AGENT_ID    = "agentSubProcess";

    private static final byte[] EXTERNAL_SCOPE_BPMN = loadBytes("/bpmn/agent-with-external-scope.bpmn");

    private AnnotationConfigApplicationContext context;
    private RepositoryService repositoryService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(
                MockEngineServices.class,
                AgentConfigAutoConfiguration.class,
                AgentDiscoveryAutoConfiguration.class);
        repositoryService = context.getBean(RepositoryService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Configuration
    static class MockEngineServices {
        @Bean RepositoryService repositoryService() { return mock(RepositoryService.class); }
        @Bean RuntimeService    runtimeService()    { return mock(RuntimeService.class); }
    }

    private static byte[] loadBytes(String classpathResource) {
        try (InputStream in = AgentExternalToolScopeIT.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Test resource not found: " + classpathResource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test resource: " + classpathResource, e);
        }
    }

    private ByteArrayInputStream externalScopeBpmn() {
        return new ByteArrayInputStream(EXTERNAL_SCOPE_BPMN);
    }

    // =========================================================================
    // Tools are resolved from the declared scope element, not the agent element
    // =========================================================================

    @Nested
    class ExternalScopeResolution {

        @Test
        void resolve_toolsBuiltFromSiblingSubProcess_notFromAgentElement() {
            // agentSubProcess has no direct activity children.
            // If the builder were (incorrectly) invoked on the agent element,
            // the catalogue would be empty — that would fail the assertion below.
            when(repositoryService.getProcessModel(PROC_DEF_ID))
                    .thenAnswer(inv -> externalScopeBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(PROC_DEF_ID, AGENT_ID).orElseThrow();

            assertFalse(catalogue.tools().isEmpty(),
                    "Catalogue must not be empty: tools should come from the 'toolRegistry' sibling, "
                    + "not from the agentSubProcess which has no activity children");
        }

        @Test
        void resolve_toolCount_matchesScopeElementChildren() {
            when(repositoryService.getProcessModel(PROC_DEF_ID))
                    .thenAnswer(inv -> externalScopeBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(PROC_DEF_ID, AGENT_ID).orElseThrow();

            // toolRegistry subprocess contains lookupCustomer and validateAddress (no sequence flows).
            assertEquals(2, catalogue.tools().size());
        }

        @Test
        void resolve_toolIds_matchActivitiesInsideToolRegistrySubProcess() {
            when(repositoryService.getProcessModel(PROC_DEF_ID))
                    .thenAnswer(inv -> externalScopeBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(PROC_DEF_ID, AGENT_ID).orElseThrow();

            List<String> toolIds = catalogue.tools().stream().map(AgentToolEntry::elementId).toList();
            assertTrue(toolIds.contains("lookupCustomer"),
                    "Expected 'lookupCustomer' in catalogue but got: " + toolIds);
            assertTrue(toolIds.contains("validateAddress"),
                    "Expected 'validateAddress' in catalogue but got: " + toolIds);
        }

        @Test
        void resolve_toolNames_matchBpmnNameAttributesInScope() {
            when(repositoryService.getProcessModel(PROC_DEF_ID))
                    .thenAnswer(inv -> externalScopeBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(PROC_DEF_ID, AGENT_ID).orElseThrow();

            AgentToolEntry lookupCustomer = catalogue.tools().stream()
                    .filter(t -> "lookupCustomer".equals(t.elementId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("lookupCustomer not found in catalogue"));

            assertEquals("Lookup Customer", lookupCustomer.name());
        }

        @Test
        void resolve_toolDescriptions_extractedFromDocumentationInScope() {
            when(repositoryService.getProcessModel(PROC_DEF_ID))
                    .thenAnswer(inv -> externalScopeBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(PROC_DEF_ID, AGENT_ID).orElseThrow();

            AgentToolEntry validateAddress = catalogue.tools().stream()
                    .filter(t -> "validateAddress".equals(t.elementId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("validateAddress not found in catalogue"));

            assertNotNull(validateAddress.description());
            assertTrue(validateAddress.description().startsWith("Validates"),
                    "Expected description to start with 'Validates' but was: " + validateAddress.description());
        }

        @Test
        void resolve_catalogueElementId_isToolScopeId_notAgentElementId() {
            // The catalogue's elementId should reflect the scope element that was built,
            // i.e. "toolRegistry", not "agentSubProcess".
            when(repositoryService.getProcessModel(PROC_DEF_ID))
                    .thenAnswer(inv -> externalScopeBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(PROC_DEF_ID, AGENT_ID).orElseThrow();

            assertEquals("toolRegistry", catalogue.elementId(),
                    "Catalogue elementId should be the tool scope element id, not the agent subprocess id");
        }
    }
}
