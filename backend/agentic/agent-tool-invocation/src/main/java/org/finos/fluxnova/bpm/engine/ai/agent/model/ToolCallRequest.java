package org.finos.fluxnova.bpm.engine.ai.agent.model;

public record ToolCallRequest(
        String toolCallId,
        String toolId
) {
}
