package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.integration;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.autoconfigure.AgentConfigEnginePlugin;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.AdHocSubProcessCatalogueBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.AgentContextSpecBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.AgentToolCatalogueBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract.BpmnExtensionContextSpecBuilder;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentContextSpecRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.runtime.AgentContextResolver;
import org.finos.fluxnova.bpm.engine.ai.agent.extract.AgentConfigExtractor;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.AdHocAgentOrchestrationParseListener;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.AgentOrchestratorEnginePlugin;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.AgentSubprocessEntryListener;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.SubprocessToolCompletionListener;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgenticAdHocSubprocessJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.AdHocSubprocessCompleter;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.AgentTerminationHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.service.AdHocActivityToolInvocationServiceImpl;
import org.finos.fluxnova.bpm.engine.ai.agent.service.ToolInvocationService;
import org.finos.fluxnova.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.finos.fluxnova.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.finos.fluxnova.bpm.engine.impl.jobexecutor.JobHandler;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import java.util.List;

/**
 * Wires all five agentic plugins manually. The plugin auto-configurations are excluded
 * because their {@code @ConditionalOnBean} checks fail due to auto-config ordering
 * (they evaluate before FluxnovaBpmAutoConfiguration creates engine service beans).
 *
 * <p>Only {@link LlmService} is mocked — everything else uses real implementations
 * backed by the in-memory Fluxnova engine.
 *
 * <p>Beans that depend on engine services (RuntimeService, RepositoryService) are
 * {@code @Lazy} to break the circular dependency: engine plugins are collected during
 * engine creation, but registries/services need the engine to exist first. The lazy
 * proxies defer resolution until first use (process deployment or job execution), by
 * which time the engine is fully initialised.
 */
@TestConfiguration
public class TestConfig {

    // -- Mock boundary --

    @Bean
    public LlmService llmService() {
        return Mockito.mock(LlmService.class);
    }

    // -- agent-config plugin --

    @Bean
    public AgentConfigExtractor agentConfigExtractor() {
        return new AgentConfigExtractor();
    }

    @Bean
    @Lazy
    public AgentConfigRegistry agentConfigRegistry(RepositoryService repositoryService,
                                                   AgentConfigExtractor extractor) {
        return new AgentConfigRegistry(repositoryService, extractor);
    }

    @Bean
    public AgentConfigEnginePlugin agentConfigEnginePlugin() {
        return new AgentConfigEnginePlugin();
    }

    // -- agent-tool-context-discovery plugin --

    @Bean
    public AgentToolCatalogueBuilder agentToolCatalogueBuilder() {
        return new AdHocSubProcessCatalogueBuilder();
    }

    @Bean
    public AgentContextSpecBuilder agentContextSpecBuilder() {
        return new BpmnExtensionContextSpecBuilder();
    }

    @Bean
    @Lazy
    public AgentToolCatalogueRegistry agentToolCatalogueRegistry(RepositoryService repositoryService,
                                                                  AgentConfigRegistry configRegistry,
                                                                  AgentToolCatalogueBuilder builder) {
        return new AgentToolCatalogueRegistry(repositoryService, configRegistry, builder);
    }

    @Bean
    @Lazy
    public AgentContextSpecRegistry agentContextSpecRegistry(RepositoryService repositoryService,
                                                              AgentConfigRegistry configRegistry,
                                                              AgentContextSpecBuilder builder) {
        return new AgentContextSpecRegistry(repositoryService, configRegistry, builder);
    }

    @Bean
    @Lazy
    public AgentContextResolver agentContextResolver(RuntimeService runtimeService) {
        return new AgentContextResolver(runtimeService);
    }

    // -- agent-tool-invocation plugin --

    @Bean
    @Lazy
    public ToolInvocationService toolInvocationService(RuntimeService runtimeService) {
        return new AdHocActivityToolInvocationServiceImpl(runtimeService);
    }

    // -- agent-orchestrator plugin --

    @Bean
    @Lazy
    public AgentStateManager agentStateManager(RuntimeService runtimeService) {
        return new AgentStateManager(runtimeService);
    }

    @Bean
    public AgentSubprocessEntryListener agentSubprocessEntryListener() {
        return new AgentSubprocessEntryListener();
    }

    @Bean
    public SubprocessToolCompletionListener subprocessToolCompletionListener(
            @Lazy AgentToolCatalogueRegistry toolCatalogueRegistry) {
        return new SubprocessToolCompletionListener(toolCatalogueRegistry);
    }

    @Bean
    @Lazy
    public AgentTerminationHandler agentTerminationHandler(RuntimeService runtimeService) {
        return new AdHocSubprocessCompleter(runtimeService);
    }

    @Bean
    public AdHocAgentOrchestrationParseListener adHocAgentOrchestrationParseListener(
            AgentSubprocessEntryListener entryListener,
            SubprocessToolCompletionListener completionListener) {
        return new AdHocAgentOrchestrationParseListener(entryListener, completionListener);
    }

    @Bean
    public AgentOrchestratorEnginePlugin agentOrchestratorEnginePlugin(
            AdHocAgentOrchestrationParseListener parseListener) {
        return new AgentOrchestratorEnginePlugin(parseListener);
    }

    // The job handler and its registration plugin are combined into a single bean.
    // The handler is constructed eagerly with @Lazy proxy references for all
    // engine-service-dependent beans. The proxies satisfy getType() (returns a
    // constant) without triggering resolution. Resolution happens at job execution
    // time, when the engine is fully available.
    //
    // Job handler registration is currently missing from AgentOrchestratorEnginePlugin
    // — this plugin fills that gap until the production code is updated.
    @Bean
    public AbstractProcessEnginePlugin jobHandlerRegistrationPlugin(
            @Lazy AgentConfigRegistry configRegistry,
            @Lazy AgentToolCatalogueRegistry toolCatalogueRegistry,
            @Lazy AgentContextSpecRegistry contextSpecRegistry,
            @Lazy AgentContextResolver contextResolver,
            LlmService llmService,
            @Lazy ToolInvocationService toolInvocationService,
            @Lazy AgentStateManager stateManager,
            @Lazy AgentTerminationHandler terminationHandler) {
        AgenticAdHocSubprocessJobHandler handler = new AgenticAdHocSubprocessJobHandler(
                configRegistry, toolCatalogueRegistry, contextSpecRegistry,
                contextResolver, llmService, toolInvocationService,
                stateManager, terminationHandler);
        return new AbstractProcessEnginePlugin() {
            @Override
            public void preInit(ProcessEngineConfigurationImpl config) {
                List<JobHandler> handlers = config.getCustomJobHandlers();
                if (handlers == null) {
                    handlers = new ArrayList<>();
                    config.setCustomJobHandlers(handlers);
                }
                handlers.add(handler);
            }
        };
    }
}
