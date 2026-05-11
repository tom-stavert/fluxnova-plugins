package org.finos.fluxnova.bpm.engine.shared.agent.model;

import java.util.List;

public record LlmResponse(
    String assistantText,
    List<ToolCallRequest> toolCalls,
    List<ConversationEntry> updatedHistory
) {}
