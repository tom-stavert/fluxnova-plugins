package org.finos.fluxnova.bpm.engine.ai.agent.integration;

import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.autoconfigure.AgentConfigAutoConfiguration;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.autoconfigure.AgentDiscoveryAutoConfiguration;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentContextSpecRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;
import org.finos.fluxnova.bpm.spring.boot.starter.event.PreUndeployEvent;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the undeploy lifecycle, verifying that a single
 * {@link PreUndeployEvent} published through the Spring application context
 * triggers both {@code AgentConfigUndeployListener} (Component 1) and
 * {@code AgentDiscoveryUndeployListener} (Component 2), clearing all three
 * registries in one step.
 *
 * <p>This cannot be tested at the unit-test level because the unit tests invoke
 * listener methods directly, bypassing the Spring event routing mechanism. Here
 * we publish the event through the context to verify the full routing chain.
 */
class AgentUndeployLifecycleIT {

    private static final String PROC_DEF_ID = "creditCheck:1:abc";
    private static final String AGENT_ID    = "creditCheckAgent";

    private static final byte[] CREDIT_CHECK_BPMN  = loadBytes("/bpmn/credit-check-agent.bpmn");
    private static final byte[] PLAIN_PROCESS_BPMN = loadBytes("/bpmn/plain-process.bpmn");

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
        try (InputStream in = AgentUndeployLifecycleIT.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Test resource not found: " + classpathResource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test resource: " + classpathResource, e);
        }
    }

    private ByteArrayInputStream creditCheckBpmn() {
        return new ByteArrayInputStream(CREDIT_CHECK_BPMN);
    }

    private ByteArrayInputStream plainProcessBpmn() {
        return new ByteArrayInputStream(PLAIN_PROCESS_BPMN);
    }

    // =========================================================================
    // Spring event routing — single event, all three listeners respond
    // =========================================================================

    @Nested
    class SpringEventRouting {

        @Test
        void singlePreUndeployEvent_clearsAllThreeRegistries() {
            // Populate all three registries by resolving against the credit-check BPMN.
            when(repositoryService.getProcessModel(PROC_DEF_ID)).thenAnswer(inv -> creditCheckBpmn());

            AgentConfigRegistry         configRegistry   = context.getBean(AgentConfigRegistry.class);
            AgentToolCatalogueRegistry  catalogueRegistry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentContextSpecRegistry    contextSpecRegistry = context.getBean(AgentContextSpecRegistry.class);

            assertTrue(configRegistry.resolve(PROC_DEF_ID, AGENT_ID).isPresent(),
                    "Pre-condition: AgentConfigRegistry must be populated before undeploy");
            assertTrue(catalogueRegistry.resolve(PROC_DEF_ID, AGENT_ID).isPresent(),
                    "Pre-condition: AgentToolCatalogueRegistry must be populated before undeploy");
            assertTrue(contextSpecRegistry.resolve(PROC_DEF_ID, AGENT_ID).isPresent(),
                    "Pre-condition: AgentContextSpecRegistry must be populated before undeploy");

            // Fire the undeploy event through the Spring application context.
            context.publishEvent(new PreUndeployEvent(mock(ProcessEngine.class)));

            // Now stub the mock to return BPMN without agent config. If any registry still
            // serves the old cached value it will return present, failing the assertion.
            when(repositoryService.getProcessModel(PROC_DEF_ID)).thenAnswer(inv -> plainProcessBpmn());

            Optional<?> configAfter      = configRegistry.resolve(PROC_DEF_ID, AGENT_ID);
            Optional<?> catalogueAfter   = catalogueRegistry.resolve(PROC_DEF_ID, AGENT_ID);
            Optional<?> contextSpecAfter = contextSpecRegistry.resolve(PROC_DEF_ID, AGENT_ID);

            assertTrue(configAfter.isEmpty(),
                    "AgentConfigRegistry must be empty after undeploy — AgentConfigUndeployListener did not fire");
            assertTrue(catalogueAfter.isEmpty(),
                    "AgentToolCatalogueRegistry must be empty after undeploy — AgentDiscoveryUndeployListener did not fire");
            assertTrue(contextSpecAfter.isEmpty(),
                    "AgentContextSpecRegistry must be empty after undeploy — AgentDiscoveryUndeployListener did not fire");
        }

        @Test
        void afterPreUndeployEvent_subsequentResolve_rescansAndReturnsLiveData() {
            // Populate registries, then undeploy and verify that the next resolve
            // re-scans rather than returning stale empty data.
            when(repositoryService.getProcessModel(PROC_DEF_ID)).thenAnswer(inv -> creditCheckBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            registry.resolve(PROC_DEF_ID, AGENT_ID);

            context.publishEvent(new PreUndeployEvent(mock(ProcessEngine.class)));

            // Re-stub with the same valid BPMN — after clearing the registry should rescan.
            when(repositoryService.getProcessModel(PROC_DEF_ID)).thenAnswer(inv -> creditCheckBpmn());

            Optional<?> result = registry.resolve(PROC_DEF_ID, AGENT_ID);
            assertTrue(result.isPresent(),
                    "Catalogue should be present after re-resolving post-undeploy: the registry must rescan");
        }
    }

    // =========================================================================
    // Verify scan counts across the undeploy boundary
    // =========================================================================

    @Nested
    class RescanBehaviour {

        @Test
        void preUndeployEvent_forcesRescanOfAgentConfigRegistry_onNextResolve() {
            // AgentConfigRegistry.resolve() calls getProcessModel() once on first access.
            // After undeploy it must call it again (not serve from cache).
            when(repositoryService.getProcessModel(PROC_DEF_ID)).thenAnswer(inv -> creditCheckBpmn());

            AgentConfigRegistry configRegistry = context.getBean(AgentConfigRegistry.class);
            configRegistry.resolve(PROC_DEF_ID, AGENT_ID); // first resolve — one scan

            context.publishEvent(new PreUndeployEvent(mock(ProcessEngine.class)));

            configRegistry.resolve(PROC_DEF_ID, AGENT_ID); // second resolve — must rescan

            // Two distinct scans: one before undeploy, one after.
            verify(repositoryService, times(2)).getProcessModel(PROC_DEF_ID);
        }

        @Test
        void preUndeployEvent_forcesRescanOfCatalogueRegistry_onNextResolve() {
            // AgentToolCatalogueRegistry.resolve() causes two getProcessModel() calls on
            // first access (one for AgentConfigRegistry, one for the catalogue scan itself).
            // After undeploy both must be repeated.
            when(repositoryService.getProcessModel(PROC_DEF_ID)).thenAnswer(inv -> creditCheckBpmn());

            AgentToolCatalogueRegistry catalogueRegistry = context.getBean(AgentToolCatalogueRegistry.class);
            catalogueRegistry.resolve(PROC_DEF_ID, AGENT_ID); // first resolve

            context.publishEvent(new PreUndeployEvent(mock(ProcessEngine.class)));

            catalogueRegistry.resolve(PROC_DEF_ID, AGENT_ID); // second resolve — must rescan

            // 2 calls before undeploy + 2 after = 4 total.
            verify(repositoryService, times(4)).getProcessModel(PROC_DEF_ID);
        }
    }
}
