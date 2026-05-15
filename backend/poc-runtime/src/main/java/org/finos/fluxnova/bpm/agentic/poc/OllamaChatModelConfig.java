package org.finos.fluxnova.bpm.agentic.poc;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OllamaChatModelConfig {

    @Bean
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        return OllamaApi.builder().baseUrl(baseUrl).build();
    }

    @Bean(name = "ollamaChatModel")
    public ChatModel ollamaChatModel(OllamaApi api) {
        ObservationRegistry registry = ObservationRegistry.NOOP;

        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
            .observationRegistry(registry)
            .toolCallbackResolver(new StaticToolCallbackResolver(List.of()))
            .toolExecutionExceptionProcessor(DefaultToolExecutionExceptionProcessor.builder().alwaysThrow(true).build())
            .build();

        return OllamaChatModel.builder()
            .ollamaApi(api)
            .defaultOptions(new OllamaOptions())
            .toolCallingManager(toolCallingManager)
            .observationRegistry(registry)
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }
}
