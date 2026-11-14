package cn.pandazi.aviation_maintenance_assistant.chat.service;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.config.AmaProperties;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievedExcerpt;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievalService;
import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GroundedChatServiceTest {

    private RetrievalService retrievalService;
    private LlmCallExecutor executor;
    private AmaProperties properties;
    private GroundedChatService service;
    private GroundedChatService.GroundedRequest request;

    @BeforeEach
    void setUp() {
        retrievalService = mock(RetrievalService.class);
        executor = mock(LlmCallExecutor.class);
        properties = new AmaProperties();
        service = new GroundedChatService(retrievalService, executor, properties);
        request = new GroundedChatService.GroundedRequest("question", "system rules", "trace-grounded");
    }

    @Test
    void emptyRetrievalSkipsGeneration() {
        when(retrievalService.retrieve("question", "trace-grounded")).thenReturn(List.of());

        GroundedChatService.GroundedResult result = service.respond(request);

        assertThat(result.outcome()).isEqualTo(ChatResponse.Outcome.NO_ANSWER);
        assertThat(result.generationMode()).isEqualTo(ChatResponse.GenerationMode.NONE);
        assertThat(result.citations()).isEmpty();
        verifyNoInteractions(executor);
    }

    @Test
    void blockingResponseIncludesSourcesAndStableCitations() {
        RetrievedExcerpt excerpt = excerpt();
        when(retrievalService.retrieve("question", "trace-grounded")).thenReturn(List.of(excerpt));
        when(executor.executeBlocking(any(ChatRequest.class), eq("trace-grounded")))
                .thenReturn(response("Grounded answer"));

        GroundedChatService.GroundedResult result = service.respond(request);

        assertThat(result.outcome()).isEqualTo(ChatResponse.Outcome.ANSWERED);
        assertThat(result.citations()).containsExactly(excerpt.toCitation());
        ArgumentCaptor<ChatRequest> chatRequest = ArgumentCaptor.forClass(ChatRequest.class);
        verify(executor).executeBlocking(chatRequest.capture(), eq("trace-grounded"));
        assertThat(chatRequest.getValue().messages()).hasSize(2);
        assertThat(((UserMessage) chatRequest.getValue().messages().get(1)).singleText())
                .contains("[S1]")
                .contains(excerpt.excerptId())
                .contains(excerpt.text());
    }

    @Test
    void disabledStreamingUsesOneBlockingCallAndMarksTheFallback() {
        properties.getLlm().setStreamingEnabled(false);
        when(retrievalService.retrieve("question", "trace-grounded")).thenReturn(List.of(excerpt()));
        when(executor.executeBlocking(any(ChatRequest.class), eq("trace-grounded")))
                .thenReturn(response("Complete answer"));
        GroundedChatService.StreamObserver observer = mock(GroundedChatService.StreamObserver.class);

        service.respondStream(request, observer);

        ArgumentCaptor<GroundedChatService.GroundedResult> result =
                ArgumentCaptor.forClass(GroundedChatService.GroundedResult.class);
        verify(observer).onToken("Complete answer");
        verify(observer).onComplete(result.capture());
        assertThat(result.getValue().degraded()).isTrue();
        assertThat(result.getValue().degradationReason()).isEqualTo("STREAMING_DISABLED");
        verify(executor).executeBlocking(any(ChatRequest.class), eq("trace-grounded"));
        verify(executor, never()).executeStreaming(any(), any(), any());
    }

    @Test
    void streamingFailureIsForwardedWithoutBlockingRetry() {
        properties.getLlm().setStreamingEnabled(true);
        when(retrievalService.retrieve("question", "trace-grounded")).thenReturn(List.of(excerpt()));
        RuntimeException failure = new RuntimeException("synthetic stream failure");
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(2);
            handler.onPartialResponse("partial");
            handler.onError(failure);
            return null;
        }).when(executor).executeStreaming(any(), eq("trace-grounded"), any());
        GroundedChatService.StreamObserver observer = mock(GroundedChatService.StreamObserver.class);

        service.respondStream(request, observer);

        verify(observer).onToken("partial");
        verify(observer).onError(failure);
        verify(executor, never()).executeBlocking(any(), any());
    }

    private RetrievedExcerpt excerpt() {
        return new RetrievedExcerpt(
                "Synthetic source text", "exr_test", "doc_test", "synthetic.md", 0, 0.8);
    }

    private dev.langchain4j.model.chat.response.ChatResponse response(String text) {
        return dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }
}
