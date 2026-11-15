package cn.pandazi.aviation_maintenance_assistant.mcp;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;

import java.util.List;

/**
 * MCP tool output for ask_maintenance_question. Isomorphic to the HTTP ChatResponse,
 * with citations extended by the original excerpt text.
 */
public record MaintenanceAnswerResult(
        String reply,
        String conversationId,
        ChatResponse.Outcome outcome,
        String traceId,
        ChatResponse.GenerationMode generationMode,
        boolean degraded,
        String degradationReason,
        List<McpCitation> citations) {

    public MaintenanceAnswerResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
