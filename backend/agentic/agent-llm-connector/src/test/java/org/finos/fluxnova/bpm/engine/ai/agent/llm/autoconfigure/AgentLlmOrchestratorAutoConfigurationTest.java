package org.finos.fluxnova.bpm.engine.ai.agent.llm.autoconfigure;

import org.finos.fluxnova.bpm.engine.ai.agent.llm.provider.AgentProviderRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.tool.AgentToolSchemaConverter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentLlmOrchestratorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AgentLlmOrchestratorAutoConfiguration.class));

    @Test
    void wiresAllBeansWhenChatModelPresent() {
        runner.withUserConfiguration(ChatModelConfig.class).run(context -> {
            assertThat(context).hasSingleBean(AgentProviderRegistry.class);
            assertThat(context).hasSingleBean(AgentToolSchemaConverter.class);
            assertThat(context).hasSingleBean(LlmService.class);

            AgentProviderRegistry registry = context.getBean(AgentProviderRegistry.class);
            assertThat(registry.has("ollama")).isTrue();
        });
    }

    @Test
    void skippedWhenNoChatModelOnContext() {
        runner.run(context -> assertThat(context).doesNotHaveBean(LlmService.class));
    }

    @Configuration
    static class ChatModelConfig {
        @Bean
        ChatModel ollamaChatModel() {
            return mock(ChatModel.class);
        }
    }
}
