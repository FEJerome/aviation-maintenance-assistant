package cn.pandazi.aviation_maintenance_assistant.mcp;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.chat.dto.Citation;
import cn.pandazi.aviation_maintenance_assistant.chat.service.MaintenanceChatService;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievedExcerpt;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievalService;
import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpMaintenanceToolTest {

    private MaintenanceChatService chatService;
    private McpMaintenanceTool tool;

    @BeforeEach
    void setUp() {
        chatService = mock(MaintenanceChatService.class);
        tool = new McpMaintenanceTool(chatService);
    }

    @Test
    void answeredOutcomeCarriesExcerptTextAsCitationEvidence() {
        RetrievedExcerpt excerpt = new RetrievedExcerpt(
                "Synthetic manual excerpt about oil pressure.",
                "excerpt-1",
                "doc-1",
                "Synthetic Manual",
                0,
                0.91);
        when(chatService.processWithExcerpts(isNull(), any()))
                .thenReturn(new MaintenanceChatService.ProcessedAnswer(
                        new ChatResponse(
                                "Synthetic answer",
                                "conversation-1",
                                ChatResponse.Outcome.ANSWERED,
                                "trace-1",
                                ChatResponse.GenerationMode.NON_STREAMING,
                                false,
                                null,
                                List.of(new Citation("excerpt-1", "doc-1", "Synthetic Manual", 0, 0.91))),
                        List.of(excerpt)));

        MaintenanceAnswerResult result = tool.askMaintenanceQuestion(
                "CTLS Rotax 912 engine oil pressure", null);

        assertThat(result.outcome()).isEqualTo(ChatResponse.Outcome.ANSWERED);
        assertThat(result.reply()).isEqualTo("Synthetic answer");
        assertThat(result.conversationId()).isEqualTo("conversation-1");
        assertThat(result.traceId()).isEqualTo("trace-1");
        assertThat(result.generationMode()).isEqualTo(ChatResponse.GenerationMode.NON_STREAMING);
        assertThat(result.citations()).hasSize(1);
        McpCitation citation = result.citations().get(0);
        assertThat(citation.excerptId()).isEqualTo("excerpt-1");
        assertThat(citation.documentId()).isEqualTo("doc-1");
        assertThat(citation.documentTitle()).isEqualTo("Synthetic Manual");
        assertThat(citation.chunkIndex()).isZero();
        assertThat(citation.score()).isEqualTo(0.91);
        assertThat(citation.text()).isEqualTo("Synthetic manual excerpt about oil pressure.");
    }

    @Test
    void blankQuestionIsRejectedBeforeAnyProcessing() {
        assertThatThrownBy(() -> tool.askMaintenanceQuestion("   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question");

        verifyNoProcessing();
    }

    @Test
    void overlongQuestionIsRejectedBeforeAnyProcessing() {
        String question = "x".repeat(McpMaintenanceTool.MAX_QUESTION_LENGTH + 1);

        assertThatThrownBy(() -> tool.askMaintenanceQuestion(question, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4000");

        verifyNoProcessing();
    }

    @Test
    void nonUuidConversationIdIsRejectedBeforeAnyProcessing() {
        assertThatThrownBy(() -> tool.askMaintenanceQuestion("oil pressure", "not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");

        verifyNoProcessing();
    }

    @Test
    void shortcutOutcomesAreMappedWithoutModelEvidence() {
        when(chatService.processWithExcerpts(any(), any()))
                .thenReturn(new MaintenanceChatService.ProcessedAnswer(
                        new ChatResponse(
                                "clarify",
                                "conversation-2",
                                ChatResponse.Outcome.CLARIFICATION_REQUIRED,
                                "trace-2",
                                ChatResponse.GenerationMode.NONE,
                                false,
                                null,
                                List.of()),
                        List.of()));

        MaintenanceAnswerResult result = tool.askMaintenanceQuestion("engine oil pressure", null);

        assertThat(result.outcome()).isEqualTo(ChatResponse.Outcome.CLARIFICATION_REQUIRED);
        assertThat(result.generationMode()).isEqualTo(ChatResponse.GenerationMode.NONE);
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void llmFailuresSurfaceOnlySafeCodeAndTraceId() {
        LlmCallExecutor.LlmCallException failure = mock(LlmCallExecutor.LlmCallException.class);
        when(failure.code()).thenReturn(LlmCallExecutor.ErrorCode.LLM_TIMEOUT);
        when(failure.getMessage()).thenReturn("The configured LLM endpoint timed out.");
        when(failure.traceId()).thenReturn("trace-llm");
        when(chatService.processWithExcerpts(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> tool.askMaintenanceQuestion("CTLS Rotax 912 engine oil pressure", null))
                .isInstanceOf(McpMaintenanceTool.McpToolFailureException.class)
                .hasMessageContaining("LLM_TIMEOUT")
                .hasMessageContaining("trace-llm")
                .hasMessageNotContaining("synthetic provider detail");
    }

    @Test
    void unexpectedFailuresBecomeInternalErrorWithoutInternals() {
        when(chatService.processWithExcerpts(any(), any()))
                .thenThrow(new RuntimeException("synthetic internal detail that must not leak"));

        assertThatThrownBy(() -> tool.askMaintenanceQuestion("CTLS Rotax 912 engine oil pressure", null))
                .isInstanceOf(McpMaintenanceTool.McpToolFailureException.class)
                .hasMessageContaining("INTERNAL_ERROR")
                .hasMessageNotContaining("synthetic internal detail");
    }

    private void verifyNoProcessing() {
        org.mockito.Mockito.verifyNoInteractions(chatService);
    }
}
