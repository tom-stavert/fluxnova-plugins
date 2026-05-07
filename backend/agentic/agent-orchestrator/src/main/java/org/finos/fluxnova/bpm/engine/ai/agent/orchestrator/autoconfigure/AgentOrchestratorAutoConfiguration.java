package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.autoconfigure;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.autoconfigure.AgentConfigAutoConfiguration;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.autoconfigure.AgentDiscoveryAutoConfiguration;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentContextSpecRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.runtime.AgentContextResolver;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.AgentOrchestrationParseListener;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.AgentOrchestratorEnginePlugin;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.AgentSubprocessEntryListener;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine.ToolCompletionListener;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.job.AgentOrchestrationJobHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.LlmOrchestrationService;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.ToolInvocationService;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {AgentConfigAutoConfiguration.class, AgentDiscoveryAutoConfiguration.class})
@ConditionalOnBean({RuntimeService.class, LlmOrchestrationService.class, ToolInvocationService.class})
public class AgentOrchestratorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentStateManager agentStateManager(RuntimeService runtimeService) {
        return new AgentStateManager(runtimeService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentSubprocessEntryListener agentSubprocessEntryListener() {
        return new AgentSubprocessEntryListener();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolCompletionListener toolCompletionListener(AgentToolCatalogueRegistry toolCatalogueRegistry) {
        return new ToolCompletionListener(toolCatalogueRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentOrchestrationJobHandler agentOrchestrationJobHandler(
            AgentConfigRegistry agentConfigRegistry,
            AgentToolCatalogueRegistry toolCatalogueRegistry,
            AgentContextSpecRegistry contextSpecRegistry,
            AgentContextResolver contextResolver,
            LlmOrchestrationService llmOrchestrationService,
            ToolInvocationService toolInvocationService,
            AgentStateManager stateManager,
            RuntimeService runtimeService) {
        return new AgentOrchestrationJobHandler(
                agentConfigRegistry, toolCatalogueRegistry, contextSpecRegistry,
                contextResolver, llmOrchestrationService, toolInvocationService,
                stateManager, runtimeService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentOrchestrationParseListener agentOrchestrationParseListener(
            AgentSubprocessEntryListener entryListener,
            ToolCompletionListener completionListener) {
        return new AgentOrchestrationParseListener(entryListener, completionListener);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentOrchestratorEnginePlugin agentOrchestratorEnginePlugin(
            AgentOrchestrationParseListener parseListener) {
        return new AgentOrchestratorEnginePlugin(parseListener);
    }
}
