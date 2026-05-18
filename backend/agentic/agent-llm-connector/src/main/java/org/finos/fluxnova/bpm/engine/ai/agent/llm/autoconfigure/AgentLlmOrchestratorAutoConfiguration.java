package org.finos.fluxnova.bpm.engine.ai.agent.llm.autoconfigure;

import org.finos.fluxnova.bpm.engine.ai.agent.llm.provider.AgentProviderProperties;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.provider.AgentProviderRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.provider.AgentProviderRegistryConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.SpringAiLlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.tool.AgentToolSchemaConverter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@AutoConfiguration
@ConditionalOnClass(ChatModel.class)
@ConditionalOnBean(ChatModel.class)
@EnableConfigurationProperties(AgentProviderProperties.class)
public class AgentLlmOrchestratorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentProviderRegistry agentProviderRegistry(Map<String, ChatModel> chatModels,
                                                       AgentProviderProperties properties) {
        return AgentProviderRegistryConfig.build(chatModels, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentToolSchemaConverter agentToolSchemaConverter() {
        return new AgentToolSchemaConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmService llmService(AgentProviderRegistry registry,
                                 AgentToolSchemaConverter converter) {
        return new SpringAiLlmService(registry, converter);
    }
}
