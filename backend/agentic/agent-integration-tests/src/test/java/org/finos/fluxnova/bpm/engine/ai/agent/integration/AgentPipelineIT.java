package org.finos.fluxnova.bpm.engine.ai.agent.integration;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.autoconfigure.AgentConfigAutoConfiguration;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.autoconfigure.AgentDiscoveryAutoConfiguration;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentContextSpec;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentContextSpecRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.runtime.AgentContextResolver;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the full agent discovery pipeline, exercising both
 * {@code agent-config} (Component 1) and {@code agent-tool-context-discovery} (Component 2)
 * wired together through their real Spring auto-configurations.
 *
 * <p>The Camunda engine services ({@link RepositoryService}, {@link RuntimeService}) are
 * the only mocked dependencies; all other beans are real instances produced by
 * {@link AgentConfigAutoConfiguration} and {@link AgentDiscoveryAutoConfiguration}.
 *
 * <p>Each test receives a fresh Spring context (created in {@code setUp}, closed in
 * {@code tearDown}) to prevent cached registry state from bleeding between tests.
 */
class AgentPipelineIT {

    private static final String CREDIT_CHECK_PROC_DEF_ID = "creditCheck:1:abc";
    private static final String CREDIT_CHECK_AGENT_ID    = "creditCheckAgent";

    private static final String MULTI_AGENT_PROC_DEF_ID = "multiAgent:1:xyz";

    // BPMN bytes loaded once per class; helper creates a fresh InputStream per call.
    private static final byte[] CREDIT_CHECK_BPMN  = loadBytes("/bpmn/credit-check-agent.bpmn");
    private static final byte[] MULTI_AGENT_BPMN   = loadBytes("/bpmn/multi-agent-process.bpmn");
    private static final byte[] PLAIN_PROCESS_BPMN = loadBytes("/bpmn/plain-process.bpmn");

    private AnnotationConfigApplicationContext context;
    private RepositoryService repositoryService;
    private RuntimeService    runtimeService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(
                MockEngineServices.class,
                AgentConfigAutoConfiguration.class,
                AgentDiscoveryAutoConfiguration.class);
        repositoryService = context.getBean(RepositoryService.class);
        runtimeService    = context.getBean(RuntimeService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    // -------------------------------------------------------------------------
    // Spring @Configuration that provides mock engine services
    // -------------------------------------------------------------------------

    @Configuration
    static class MockEngineServices {
        @Bean RepositoryService repositoryService() { return mock(RepositoryService.class); }
        @Bean RuntimeService    runtimeService()    { return mock(RuntimeService.class); }
    }

    // -------------------------------------------------------------------------
    // BPMN loading helpers
    // -------------------------------------------------------------------------

    private static byte[] loadBytes(String classpathResource) {
        try (InputStream in = AgentPipelineIT.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Test resource not found: " + classpathResource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test resource: " + classpathResource, e);
        }
    }

    /** Returns a fresh {@link ByteArrayInputStream} backed by the credit-check-agent BPMN. */
    private ByteArrayInputStream creditCheckBpmn() {
        return new ByteArrayInputStream(CREDIT_CHECK_BPMN);
    }

    /** Returns a fresh {@link ByteArrayInputStream} backed by the multi-agent BPMN. */
    private ByteArrayInputStream multiAgentBpmn() {
        return new ByteArrayInputStream(MULTI_AGENT_BPMN);
    }

    /** Returns a fresh {@link ByteArrayInputStream} backed by the plain (no-agent) BPMN. */
    private ByteArrayInputStream plainProcessBpmn() {
        return new ByteArrayInputStream(PLAIN_PROCESS_BPMN);
    }

    /** Finds a tool by element ID, throwing if absent (keeps test bodies concise). */
    private static AgentToolEntry findTool(AgentToolCatalogue catalogue, String elementId) {
        return catalogue.tools().stream()
                .filter(t -> elementId.equals(t.elementId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected tool '" + elementId + "' in catalogue but it was absent. "
                        + "Actual tools: " + catalogue.tools().stream()
                                .map(AgentToolEntry::elementId).toList()));
    }

    // =========================================================================
    // Tool catalogue discovery
    // =========================================================================

    @Nested
    class CatalogueDiscovery {

        @Test
        void resolve_returnsOnlyEligibleTools_excludingSequenceFlowTargets() {
            // assessRisk has two incoming sequence flows and must be excluded from the catalogue.
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> creditCheckBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID)
                    .orElseThrow();

            List<String> toolIds = catalogue.tools().stream().map(AgentToolEntry::elementId).toList();
            assertEquals(2, toolIds.size());
            assertTrue(toolIds.contains("fetchCreditScore"));
            assertTrue(toolIds.contains("checkBlacklist"));
            assertFalse(toolIds.contains("assessRisk"),
                    "assessRisk is a sequence-flow target and must not appear in the catalogue");
        }

        @Test
        void resolve_toolNames_matchBpmnNameAttribute() {
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> creditCheckBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID)
                    .orElseThrow();

            assertEquals("Fetch Credit Score", findTool(catalogue, "fetchCreditScore").name());
            assertEquals("Check Blacklist",    findTool(catalogue, "checkBlacklist").name());
        }

        @Test
        void resolve_toolDescriptions_extractedFromDocumentationElement() {
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> creditCheckBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID)
                    .orElseThrow();

            String fetchDesc = findTool(catalogue, "fetchCreditScore").description();
            assertNotNull(fetchDesc);
            assertTrue(fetchDesc.startsWith("Retrieves the credit score"),
                    "Expected description to start with 'Retrieves the credit score' but was: " + fetchDesc);

            String blacklistDesc = findTool(catalogue, "checkBlacklist").description();
            assertNotNull(blacklistDesc);
            assertTrue(blacklistDesc.startsWith("Checks whether"),
                    "Expected description to start with 'Checks whether' but was: " + blacklistDesc);
        }

        @Test
        void resolve_reads_extractedFromInputParameterExpressions() {
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> creditCheckBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID)
                    .orElseThrow();

            assertEquals(Set.of("customerId"), findTool(catalogue, "fetchCreditScore").reads());
            assertEquals(Set.of("customerId"), findTool(catalogue, "checkBlacklist").reads());
        }

        @Test
        void resolve_writes_extractedFromOutputParameterNames() {
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> creditCheckBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID)
                    .orElseThrow();

            assertEquals(Set.of("creditScore"), findTool(catalogue, "fetchCreditScore").writes());
            assertEquals(Set.of("blacklisted"), findTool(catalogue, "checkBlacklist").writes());
        }

        @Test
        void resolve_whenProcessHasNoAgentConfig_returnsEmpty() {
            // plain-process.bpmn has no agent:config; AgentConfigRegistry returns empty,
            // so AgentToolCatalogueRegistry must propagate that as an empty Optional.
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> plainProcessBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            Optional<AgentToolCatalogue> result = registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID);

            assertTrue(result.isEmpty());
        }

        @Test
        void resolve_catalogueMetadata_processDefinitionIdAndElementIdAreSet() {
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> creditCheckBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);
            AgentToolCatalogue catalogue = registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID)
                    .orElseThrow();

            assertEquals(CREDIT_CHECK_PROC_DEF_ID, catalogue.processDefinitionId());
            assertEquals(CREDIT_CHECK_AGENT_ID,    catalogue.elementId());
        }
    }

    // =========================================================================
    // Context spec discovery
    // =========================================================================

    @Nested
    class ContextSpecDiscovery {

        @Test
        void resolve_returnsDeclaredVariables_fromAgentContextElement() {
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> creditCheckBpmn());

            AgentContextSpecRegistry registry = context.getBean(AgentContextSpecRegistry.class);
            AgentContextSpec spec = registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID)
                    .orElseThrow();

            List<String> names = spec.declaredVariables().stream()
                    .map(v -> v.name())
                    .toList();
            assertEquals(List.of("customerId", "applicationAmount"), names);
        }

        @Test
        void resolve_contextSpecMetadata_processDefinitionIdAndElementIdAreSet() {
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> creditCheckBpmn());

            AgentContextSpecRegistry registry = context.getBean(AgentContextSpecRegistry.class);
            AgentContextSpec spec = registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID)
                    .orElseThrow();

            assertEquals(CREDIT_CHECK_PROC_DEF_ID, spec.processDefinitionId());
            assertEquals(CREDIT_CHECK_AGENT_ID,    spec.elementId());
        }

        @Test
        void resolve_whenNoAgentConfig_returnsEmpty() {
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> plainProcessBpmn());

            AgentContextSpecRegistry registry = context.getBean(AgentContextSpecRegistry.class);
            assertTrue(registry.resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID).isEmpty());
        }
    }

    // =========================================================================
    // Context resolution (AgentContextSpec from BPMN → AgentContextResolver)
    // =========================================================================

    @Nested
    class ContextResolution {

        private static final String EXECUTION_ID = "exec-001";

        /**
         * Provides a representative set of runtime variables, including internal
         * {@code _agent*} variables that the resolver must always strip.
         */
        private Map<String, Object> runtimeVariables() {
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("customerId",         "C-001");
            vars.put("applicationAmount",  75_000);
            vars.put("creditScore",        720);
            vars.put("_agentState",        "RUNNING");
            vars.put("_agentConversation", List.of("msg1"));
            return vars;
        }

        @Test
        void resolve_withDeclaredContextSpec_filtersToExplicitVariablesOnly() {
            // The spec declares customerId and applicationAmount; creditScore and _agent*
            // must be absent from the resolved context.
            when(repositoryService.getProcessModel(CREDIT_CHECK_PROC_DEF_ID))
                    .thenAnswer(inv -> creditCheckBpmn());
            when(runtimeService.getVariables(EXECUTION_ID)).thenReturn(runtimeVariables());

            AgentContextSpec spec = context.getBean(AgentContextSpecRegistry.class)
                    .resolve(CREDIT_CHECK_PROC_DEF_ID, CREDIT_CHECK_AGENT_ID)
                    .orElseThrow();

            ResolvedContext resolved = context.getBean(AgentContextResolver.class)
                    .resolve(EXECUTION_ID, spec);

            assertEquals(2, resolved.variables().size());
            assertEquals("C-001",  resolved.variables().get("customerId"));
            assertEquals(75_000,   resolved.variables().get("applicationAmount"));
            assertFalse(resolved.variables().containsKey("creditScore"),
                    "creditScore is not declared and must be excluded");
            assertFalse(resolved.variables().containsKey("_agentState"),
                    "_agent-prefixed variables must always be excluded");
        }

        @Test
        void resolve_withUndeclaredContextSpec_exposesAllNonAgentPrefixedVariables() {
            // multi-agent-process.bpmn has no agent:context on agentA, so declaredVariables
            // is empty → expose all non-_agent* variables.
            when(repositoryService.getProcessModel(MULTI_AGENT_PROC_DEF_ID))
                    .thenAnswer(inv -> multiAgentBpmn());
            when(runtimeService.getVariables(EXECUTION_ID)).thenReturn(runtimeVariables());

            AgentContextSpec spec = context.getBean(AgentContextSpecRegistry.class)
                    .resolve(MULTI_AGENT_PROC_DEF_ID, "agentA")
                    .orElseThrow();

            assertTrue(spec.declaredVariables().isEmpty(),
                    "agentA has no agent:context so declared variables must be empty");

            ResolvedContext resolved = context.getBean(AgentContextResolver.class)
                    .resolve(EXECUTION_ID, spec);

            // All three non-agent variables should appear; _agent* must not.
            assertEquals(3, resolved.variables().size());
            assertTrue(resolved.variables().containsKey("customerId"));
            assertTrue(resolved.variables().containsKey("applicationAmount"));
            assertTrue(resolved.variables().containsKey("creditScore"));
            assertFalse(resolved.variables().containsKey("_agentState"));
            assertFalse(resolved.variables().containsKey("_agentConversation"));
        }
    }

    // =========================================================================
    // Multiple agents in one process definition
    // =========================================================================

    @Nested
    class MultipleAgentsInOneProcess {

        @Test
        void eachAgent_resolvesItsOwnCatalogue_independently() {
            when(repositoryService.getProcessModel(MULTI_AGENT_PROC_DEF_ID))
                    .thenAnswer(inv -> multiAgentBpmn());

            AgentToolCatalogueRegistry registry = context.getBean(AgentToolCatalogueRegistry.class);

            AgentToolCatalogue catalogueA = registry.resolve(MULTI_AGENT_PROC_DEF_ID, "agentA").orElseThrow();
            AgentToolCatalogue catalogueB = registry.resolve(MULTI_AGENT_PROC_DEF_ID, "agentB").orElseThrow();

            assertEquals(1, catalogueA.tools().size());
            assertEquals("toolA1", catalogueA.tools().get(0).elementId());

            assertEquals(2, catalogueB.tools().size());
            List<String> bToolIds = catalogueB.tools().stream().map(AgentToolEntry::elementId).toList();
            assertTrue(bToolIds.contains("toolB1"));
            assertTrue(bToolIds.contains("toolB2"));
        }

        @Test
        void eachAgent_hasCorrectProvider_fromItsOwnConfig() {
            when(repositoryService.getProcessModel(MULTI_AGENT_PROC_DEF_ID))
                    .thenAnswer(inv -> multiAgentBpmn());

            // The two agents have different providers; resolving one must not return the
            // other's config (verifies the per-element keying in AgentConfigRegistry).
            var configA = context.getBean(AgentConfigRegistry.class)
                    .resolve(MULTI_AGENT_PROC_DEF_ID, "agentA").orElseThrow();
            var configB = context.getBean(AgentConfigRegistry.class)
                    .resolve(MULTI_AGENT_PROC_DEF_ID, "agentB").orElseThrow();

            assertEquals("anthropic", configA.provider());
            assertEquals("ollama",    configB.provider());
        }
    }
}
