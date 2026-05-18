package org.finos.fluxnova.bpm.engine.ai.agent.llm.provider;

import org.springframework.ai.chat.model.ChatModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

/**
 * Builds an {@link AgentProviderRegistry} from the {@link ChatModel} beans on the classpath.
 *
 * <p>Auto-discovery: any bean whose name ends in {@code ChatModel} is registered under the
 * lowercase prefix (e.g. {@code ollamaChatModel} → provider id {@code ollama}).</p>
 *
 * <p>Overrides from {@link AgentProviderProperties} take precedence over auto-discovery and
 * may also be used to remap a standard provider to a different bean instance.</p>
 *
 * <p>This is the only place in the codebase aware of Spring AI's bean naming convention.
 * Downstream code works with {@code ChatModel} only — fully provider-agnostic.</p>
 */
public final class AgentProviderRegistryConfig {

    private static final String CHAT_MODEL_SUFFIX = "ChatModel";

    private AgentProviderRegistryConfig() {}

    public static AgentProviderRegistry build(Map<String, ChatModel> chatModels,
                                              AgentProviderProperties properties) {
        Map<String, ChatModel> registry = new HashMap<>();

        chatModels.forEach((beanName, model) -> {
            if (beanName.endsWith(CHAT_MODEL_SUFFIX) && beanName.length() > CHAT_MODEL_SUFFIX.length()) {
                String providerId = beanName
                    .substring(0, beanName.length() - CHAT_MODEL_SUFFIX.length())
                    .toLowerCase(Locale.ROOT);
                registry.put(providerId, model);
            }
        });

        properties.getProviderOverrides().forEach((providerId, beanName) -> {
            ChatModel model = chatModels.get(beanName);
            if (model != null) {
                registry.put(providerId, model);
            }
        });

        return new AgentProviderRegistry(registry);
    }
}
