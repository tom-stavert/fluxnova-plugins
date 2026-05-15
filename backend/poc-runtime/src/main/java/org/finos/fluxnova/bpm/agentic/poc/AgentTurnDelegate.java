package org.finos.fluxnova.bpm.agentic.poc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentContextSpec;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentContextSpecRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.runtime.ContextResolver;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.model.ConversationEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.model.LlmResponse;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.ai.agent.registry.AgentConfigRegistry;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component("agentTurnDelegate")
public class AgentTurnDelegate implements JavaDelegate {

    private static final String AGENT_ELEMENT_ID = "agentContainer";
    private static final String VAR_STATUS = "_agent.status";
    private static final String VAR_TURN = "_agent.turn";
    private static final String VAR_MAX_TURNS = "_agent.maxTurns";
    private static final String VAR_HISTORY = "_agent.history";
    private static final String VAR_LAST_ASSISTANT_TEXT = "_agent.lastAssistantText";
    private static final List<String> INITIAL_MESSAGE_VARS = List.of("applicantMessage", "userQuestion");

    private final ObjectMapper objectMapper;
    private final AgentConfigRegistry agentConfigRegistry;
    private final AgentContextSpecRegistry contextSpecRegistry;
    private final ContextResolver contextResolver;
    private final LlmService llmService;

    public AgentTurnDelegate(ObjectMapper objectMapper,
                             AgentConfigRegistry agentConfigRegistry,
                             AgentContextSpecRegistry contextSpecRegistry,
                             ContextResolver contextResolver,
                             LlmService llmService) {
        this.objectMapper = objectMapper;
        this.agentConfigRegistry = agentConfigRegistry;
        this.contextSpecRegistry = contextSpecRegistry;
        this.contextResolver = contextResolver;
        this.llmService = llmService;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        int maxTurns = intVar(execution, VAR_MAX_TURNS).orElse(3);
        int turn = intVar(execution, VAR_TURN).orElse(0);

        if (turn >= maxTurns) {
            execution.setVariable(VAR_STATUS, "DONE");
            return;
        }

        String processDefinitionId = execution.getProcessDefinitionId();
        AgentConfig config = agentConfigRegistry.resolve(processDefinitionId, AGENT_ELEMENT_ID)
            .orElseThrow(() -> new IllegalStateException(
                "No agent config found for processDefinitionId=" + processDefinitionId + ", elementId=" + AGENT_ELEMENT_ID));

        AgentContextSpec spec = contextSpecRegistry.resolve(processDefinitionId, AGENT_ELEMENT_ID)
            .orElseGet(() -> new AgentContextSpec(processDefinitionId, AGENT_ELEMENT_ID, List.of()));

        ResolvedContext context = contextResolver.resolve(execution.getId(), spec);

        List<ConversationEntry> history = readHistory(execution);
        if (history.isEmpty()) {
            for (String varName : INITIAL_MESSAGE_VARS) {
                Object q = execution.getVariable(varName);
                if (q != null && !q.toString().isBlank()) {
                    history = List.of(ConversationEntry.user(q.toString()));
                    break;
                }
            }
        }

        execution.setVariable(VAR_STATUS, "RUNNING");
        LlmResponse response = llmService.call(config, context, history);

        execution.setVariable(VAR_HISTORY, objectMapper.writeValueAsString(response.updatedHistory()));
        execution.setVariable(VAR_LAST_ASSISTANT_TEXT, response.assistantText());
        execution.setVariable(VAR_TURN, turn + 1);

        if (response.toolCalls().isEmpty()) {
            execution.setVariable(VAR_STATUS, "DONE");
        } else {
            execution.setVariable(VAR_STATUS, "WAITING_FOR_TOOLS");
        }
    }

    private List<ConversationEntry> readHistory(DelegateExecution execution) {
        Object raw = execution.getVariable(VAR_HISTORY);
        if (raw == null) return List.of();
        String json = raw.toString();
        if (json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ConversationEntry>>() {});
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static Optional<Integer> intVar(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value == null) return Optional.empty();
        if (value instanceof Number n) return Optional.of(n.intValue());
        try {
            return Optional.of(Integer.parseInt(value.toString().trim()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
