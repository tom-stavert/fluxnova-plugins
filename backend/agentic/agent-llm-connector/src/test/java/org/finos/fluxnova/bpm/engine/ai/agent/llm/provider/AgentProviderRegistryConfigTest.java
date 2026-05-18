package org.finos.fluxnova.bpm.engine.ai.agent.llm.provider;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgentProviderRegistryConfigTest {

    @Test
    void derivesProviderIdFromBeanNameSuffix() {
        ChatModel ollama = mock(ChatModel.class);
        ChatModel huggingface = mock(ChatModel.class);

        AgentProviderRegistry registry = AgentProviderRegistryConfig.build(
            Map.of("ollamaChatModel", ollama, "huggingfaceChatModel", huggingface),
            new AgentProviderProperties());

        assertSame(ollama, registry.get("ollama"));
        assertSame(huggingface, registry.get("huggingface"));
    }

    @Test
    void ignoresBeansNotEndingInChatModel() {
        ChatModel embedding = mock(ChatModel.class);
        AgentProviderRegistry registry = AgentProviderRegistryConfig.build(
            Map.of("ollamaEmbeddingModel", embedding),
            new AgentProviderProperties());

        assertFalse(registry.has("ollamaembedding"));
    }

    @Test
    void overridesTakePrecedenceOverAutoDiscovery() {
        ChatModel autoBean = mock(ChatModel.class);
        ChatModel customBean = mock(ChatModel.class);

        Map<String, ChatModel> beans = new LinkedHashMap<>();
        beans.put("ollamaChatModel", autoBean);
        beans.put("customOllamaBean", customBean);

        AgentProviderProperties props = new AgentProviderProperties();
        Map<String, String> overrides = new HashMap<>();
        overrides.put("ollama", "customOllamaBean");
        props.setProviderOverrides(overrides);

        AgentProviderRegistry registry = AgentProviderRegistryConfig.build(beans, props);

        assertSame(customBean, registry.get("ollama"));
    }

    @Test
    void overridesAddNewProviderIds() {
        ChatModel custom = mock(ChatModel.class);
        AgentProviderProperties props = new AgentProviderProperties();
        props.setProviderOverrides(Map.of("my-provider", "myCustomBean"));

        AgentProviderRegistry registry = AgentProviderRegistryConfig.build(
            Map.of("myCustomBean", custom),
            props);

        assertSame(custom, registry.get("my-provider"));
    }
}
