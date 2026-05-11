package org.finos.fluxnova.bpm.engine.shared.agent.model;

public record ToolCallRequest(
    String toolCallId,
    String toolId
) {}
