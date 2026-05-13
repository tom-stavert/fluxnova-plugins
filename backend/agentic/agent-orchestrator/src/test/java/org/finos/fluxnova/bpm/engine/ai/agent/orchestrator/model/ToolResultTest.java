package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Nested
    class NormalConstruction {

        @Test
        void recordFieldsAreAccessible() {
            ToolResult result = new ToolResult("tc-1", "taskA", Map.of("score", 750), null);

            assertEquals("tc-1", result.toolCallId());
            assertEquals("taskA", result.toolElementId());
            assertEquals(750, result.outputs().get("score"));
            assertNull(result.errorMessage());
        }

        @Test
        void isError_returnsFalseWhenNoErrorMessage() {
            ToolResult result = new ToolResult("tc-1", "taskA", Map.of(), null);

            assertFalse(result.isError());
        }
    }

    @Nested
    class ErrorFactory {

        @Test
        void error_setsErrorMessageAndEmptyOutputs() {
            ToolResult result = ToolResult.error("tc-99", "Tool not found");

            assertEquals("tc-99", result.toolCallId());
            assertNull(result.toolElementId());
            assertTrue(result.outputs().isEmpty());
            assertEquals("Tool not found", result.errorMessage());
        }

        @Test
        void error_isErrorReturnsTrue() {
            ToolResult result = ToolResult.error("tc-1", "Failure reason");

            assertTrue(result.isError());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void constructionWithEmptyOutputs() {
            ToolResult result = new ToolResult("tc-1", "taskA", Map.of(), null);

            assertTrue(result.outputs().isEmpty());
            assertFalse(result.isError());
        }

        @Test
        void constructionWithMultipleOutputs() {
            Map<String, Object> outputs = Map.of(
                    "score", 750,
                    "approved", true,
                    "reason", "Good credit");
            ToolResult result = new ToolResult("tc-1", "creditCheck", outputs, null);

            assertEquals(3, result.outputs().size());
            assertEquals(750, result.outputs().get("score"));
            assertEquals(true, result.outputs().get("approved"));
            assertEquals("Good credit", result.outputs().get("reason"));
        }

        @Test
        void constructionWithBothOutputsAndErrorMessage() {
            ToolResult result = new ToolResult("tc-1", "taskA", Map.of("partial", "data"), "Timed out");

            assertTrue(result.isError());
            assertEquals("Timed out", result.errorMessage());
            assertEquals("data", result.outputs().get("partial"));
        }
    }
}
