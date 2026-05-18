package org.finos.fluxnova.bpm.engine.ai.agent.llm.provider;

import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

/**
 * Provides the {@link ChatModel} for a given agent provider id.
 *
 * <p>The registry is populated at startup by {@link AgentProviderRegistryConfig}, deriving
 * provider ids from Spring AI's {@code <provider>ChatModel} bean naming convention.
 * Explicit overrides via {@link AgentProviderProperties} take precedence.</p>
 */
public class AgentProviderRegistry {

    private final Map<String, ChatModel> registry;

    public AgentProviderRegistry(Map<String, ChatModel> registry) {
        this.registry = Map.copyOf(registry);
    }

    public ChatModel get(String providerId) {
        ChatModel model = registry.get(providerId);
        if (model == null) {
            throw new IllegalStateException(
                "No ChatModel configured for provider '" + providerId + "'. " +
                "Ensure the Spring AI starter for this provider is on the classpath " +
                "and credentials are configured, or add an explicit override via " +
                "fluxnova.ai.agent.provider-overrides.");
        }
        return model;
    }

    public boolean has(String providerId) {
        return registry.containsKey(providerId);
    }
}
