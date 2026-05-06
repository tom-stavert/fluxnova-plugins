package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model;

import java.util.Map;

public record ToolResult(
    String toolCallId,
    String toolElementId,
    Map<String, Object> outputs,
    String errorMessage
) {
    public static ToolResult error(String toolCallId, String message) {
        return new ToolResult(toolCallId, null, Map.of(), message);
    }

    public boolean isError() {
        return errorMessage != null;
    }
}
