package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.shared.model.ConversationEntry;
import org.finos.fluxnova.bpm.engine.shared.model.LlmResponse;
import java.util.List;

/**
 * Component 3 interface — LLM orchestration service.
 *
 * <p>
 * Called by the orchestrator to consult the LLM. Receives all necessary context (config, tools,
 * resolved variables, conversation history) and returns the LLM's response plus the updated
 * conversation history.
 */
public interface LlmOrchestrationService {

    LlmResponse call(AgentConfig agentConfig, AgentToolCatalogue catalogue, ResolvedContext context,
            List<ConversationEntry> conversationHistory);
}
