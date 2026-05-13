package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.fluxnova.bpm.engine.impl.jobexecutor.JobHandlerConfiguration;

public record AgentOrchestrationConfig(
        String scopeExecutionId,
        ToolResult toolResult
) implements JobHandlerConfiguration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonCreator
    public AgentOrchestrationConfig(
            @JsonProperty("scopeExecutionId") String scopeExecutionId,
            @JsonProperty("toolResult") ToolResult toolResult) {
    }

    public static AgentOrchestrationConfig forEntry(String scopeExecutionId) {
        return new AgentOrchestrationConfig(scopeExecutionId, null);
    }

    public static AgentOrchestrationConfig forToolCompletion(String scopeExecutionId, ToolResult result) {
        return new AgentOrchestrationConfig(scopeExecutionId, result);
    }

    public boolean hasToolResult() {
        return toolResult != null;
    }

    @Override
    public String toCanonicalString() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AgentOrchestrationConfig", e);
        }
    }

    public static AgentOrchestrationConfig fromCanonicalString(String canonicalString) {
        try {
            return MAPPER.readValue(canonicalString, AgentOrchestrationConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize AgentOrchestrationConfig", e);
        }
    }
}
