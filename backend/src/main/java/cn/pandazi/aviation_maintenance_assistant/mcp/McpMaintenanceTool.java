package cn.pandazi.aviation_maintenance_assistant.mcp;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.chat.service.MaintenanceChatService;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievedExcerpt;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievalService;
import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Read-only MCP tool exposing the maintenance question-answering pipeline. Reuses the
 * HTTP chat preparation, grounding, quota accounting, and citation logic; blocking only.
 * Model credentials stay server-side, and neither questions nor manual excerpts are logged.
 */
@Component
public class McpMaintenanceTool {

    static final int MAX_QUESTION_LENGTH = 4000;

    private static final Pattern CONVERSATION_ID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final MaintenanceChatService chatService;

    public McpMaintenanceTool(MaintenanceChatService chatService) {
        this.chatService = chatService;
    }

    @McpTool(
            name = "ask_maintenance_question",
            description = "Answer a maintenance question using the ingested manual corpus. "
                    + "Requires prior aircraft/engine validation when the question depends on them; "
                    + "returns the answer plus the original manual excerpts used as evidence. "
                    + "Read-only: never modifies manuals, configuration, or server state.")
    public MaintenanceAnswerResult askMaintenanceQuestion(
            @McpToolParam(required = true, description = "Maintenance question, 1-4000 characters.")
            String question,
            @McpToolParam(required = false,
                    description = "Optional conversation id (UUID) to reuse validated aircraft context.")
            String conversationId) {
        String validatedQuestion = validateQuestion(question);
        String validatedConversationId = validateConversationId(conversationId);

        try {
            MaintenanceChatService.ProcessedAnswer answer =
                    chatService.processWithExcerpts(validatedConversationId, validatedQuestion);
            return toResult(answer);
        } catch (LlmCallExecutor.LlmCallException exception) {
            throw new McpToolFailureException(
                    exception.code().name(), exception.getMessage(), exception.traceId());
        } catch (RetrievalService.RetrievalException exception) {
            throw new McpToolFailureException(
                    exception.code(), exception.getMessage(), exception.traceId());
        } catch (RuntimeException exception) {
            throw new McpToolFailureException(
                    "INTERNAL_ERROR",
                    "The request could not be completed.",
                    UUID.randomUUID().toString());
        }
    }

    private String validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank.");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException(
                    "question must not exceed " + MAX_QUESTION_LENGTH + " characters.");
        }
        return question;
    }

    private String validateConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        if (!CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
            throw new IllegalArgumentException("conversationId must be a UUID.");
        }
        return conversationId;
    }

    private MaintenanceAnswerResult toResult(MaintenanceChatService.ProcessedAnswer answer) {
        ChatResponse response = answer.response();
        List<McpCitation> citations = answer.excerpts().stream()
                .map(this::toCitation)
                .toList();
        return new MaintenanceAnswerResult(
                response.reply(),
                response.conversationId(),
                response.outcome(),
                response.traceId(),
                response.generationMode(),
                response.degraded(),
                response.degradationReason(),
                citations);
    }

    private McpCitation toCitation(RetrievedExcerpt excerpt) {
        return new McpCitation(
                excerpt.excerptId(),
                excerpt.documentId(),
                excerpt.documentTitle(),
                excerpt.chunkIndex(),
                excerpt.score(),
                excerpt.text());
    }

    /**
     * Carries only safe, client-actionable details. The MCP framework turns this into an
     * isError tool result whose text is the exception message.
     */
    static class McpToolFailureException extends RuntimeException {

        McpToolFailureException(String code, String message, String traceId) {
            super(code + ": " + message + " (traceId=" + traceId + ")");
        }
    }
}
