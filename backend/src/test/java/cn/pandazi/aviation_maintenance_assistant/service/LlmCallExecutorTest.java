package cn.pandazi.aviation_maintenance_assistant.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LlmCallExecutorTest {

    private ChatModel chatModel;
    private StreamingChatModel streamingChatModel;
    private LlmQuotaService quotaService;
    private LlmCallExecutor executor;
    private ChatRequest request;
    private ChatResponse response;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        streamingChatModel = mock(StreamingChatModel.class);
        quotaService = mock(LlmQuotaService.class);
        executor = new LlmCallExecutor(chatModel, streamingChatModel, quotaService);
        request = ChatRequest.builder()
                .messages(UserMessage.from("synthetic question"))
                .build();
        response = ChatResponse.builder()
                .aiMessage(AiMessage.from("synthetic answer"))
                .build();
    }

    @Test
    void blockingCallReservesQuotaBeforeInvokingTheModel() {
        when(chatModel.chat(request)).thenReturn(response);

        ChatResponse result = executor.executeBlocking(request, "trace-blocking");

        assertSame(response, result);
        InOrder order = inOrder(quotaService, chatModel);
        order.verify(quotaService).acquire();
        order.verify(chatModel).chat(request);
    }

    @Test
    void separateBlockingAttemptsEachConsumeQuota() {
        when(chatModel.chat(request)).thenReturn(response);

        executor.executeBlocking(request, "trace-transform");
        executor.executeBlocking(request, "trace-generation");

        verify(quotaService, times(2)).acquire();
        verify(chatModel, times(2)).chat(request);
    }

    @Test
    void streamingConsumesOneAttemptRegardlessOfTokenCount() {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("first ");
            handler.onPartialResponse("second ");
            handler.onPartialResponse("third");
            handler.onCompleteResponse(response);
            return null;
        }).when(streamingChatModel).chat(eq(request), any(StreamingChatResponseHandler.class));
        StreamingChatResponseHandler downstream = mock(StreamingChatResponseHandler.class);

        executor.executeStreaming(request, "trace-stream", downstream);

        verify(quotaService, times(1)).acquire();
        verify(downstream).onPartialResponse("first ");
        verify(downstream).onPartialResponse("second ");
        verify(downstream).onPartialResponse("third");
        verify(downstream).onCompleteResponse(response);
        verify(chatModel, never()).chat(any(ChatRequest.class));
    }

    @Test
    void asynchronousStreamFailureIsSafeAndNeverFallsBack() {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("provider-body-credential-marker"));
            return null;
        }).when(streamingChatModel).chat(eq(request), any(StreamingChatResponseHandler.class));
        StreamingChatResponseHandler downstream = mock(StreamingChatResponseHandler.class);

        executor.executeStreaming(request, "trace-stream-error", downstream);

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(downstream).onError(error.capture());
        LlmCallExecutor.LlmCallException callException =
                (LlmCallExecutor.LlmCallException) error.getValue();
        assertEquals(LlmCallExecutor.ErrorCode.LLM_STREAM_INTERRUPTED, callException.code());
        assertFalse(callException.getMessage().contains("provider-body-credential-marker"));
        verify(streamingChatModel, times(1))
                .chat(eq(request), any(StreamingChatResponseHandler.class));
        verify(chatModel, never()).chat(any(ChatRequest.class));
        verify(quotaService, times(1)).acquire();
    }

    @Test
    void synchronousStreamingRejectionIsUnsupportedAndIsNotRetried() {
        doThrow(new HttpException(400, "provider-body-credential-marker"))
                .when(streamingChatModel)
                .chat(eq(request), any(StreamingChatResponseHandler.class));
        StreamingChatResponseHandler downstream = mock(StreamingChatResponseHandler.class);

        LlmCallExecutor.LlmCallException exception = assertThrows(
                LlmCallExecutor.LlmCallException.class,
                () -> executor.executeStreaming(request, "trace-stream-rejected", downstream));

        assertEquals(LlmCallExecutor.ErrorCode.LLM_STREAMING_UNSUPPORTED, exception.code());
        assertFalse(exception.getMessage().contains("provider-body-credential-marker"));
        verify(streamingChatModel, times(1))
                .chat(eq(request), any(StreamingChatResponseHandler.class));
        verify(chatModel, never()).chat(any(ChatRequest.class));
        verifyNoInteractions(downstream);
    }

    @ParameterizedTest
    @MethodSource("blockingErrors")
    void normalizesBlockingErrorsWithoutPropagatingProviderBodies(
            RuntimeException providerError, LlmCallExecutor.ErrorCode expectedCode) {
        when(chatModel.chat(request)).thenThrow(providerError);

        LlmCallExecutor.LlmCallException exception = assertThrows(
                LlmCallExecutor.LlmCallException.class,
                () -> executor.executeBlocking(request, "trace-error"));

        assertEquals(expectedCode, exception.code());
        assertFalse(exception.getMessage().contains("provider-body-credential-marker"));
        verify(quotaService).acquire();
    }

    @Test
    void quotaFailuresPreventAnyModelInvocation() {
        doThrow(new LlmQuotaService.QuotaExhaustedException()).when(quotaService).acquire();

        LlmCallExecutor.LlmCallException exhausted = assertThrows(
                LlmCallExecutor.LlmCallException.class,
                () -> executor.executeBlocking(request, "trace-quota"));
        assertEquals(LlmCallExecutor.ErrorCode.QUOTA_EXHAUSTED, exhausted.code());
        verifyNoInteractions(chatModel, streamingChatModel);
    }

    @Test
    void unavailableQuotaStateAlsoPreventsAnyModelInvocation() {
        doThrow(new LlmQuotaService.QuotaStateUnavailableException()).when(quotaService).acquire();

        LlmCallExecutor.LlmCallException unavailable = assertThrows(
                LlmCallExecutor.LlmCallException.class,
                () -> executor.executeStreaming(
                        request, "trace-quota-state", mock(StreamingChatResponseHandler.class)));
        assertEquals(LlmCallExecutor.ErrorCode.QUOTA_STATE_UNAVAILABLE, unavailable.code());
        verifyNoInteractions(chatModel, streamingChatModel);
    }

    @Test
    void failedAttemptConsumesQuotaBeforeTheNextCall(@TempDir Path tempDir) throws Exception {
        Path stateFile = tempDir.resolve("state/daily-llm-quota.json");
        LlmQuotaService realQuota = new LlmQuotaService(
                stateFile,
                1,
                true,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                new ObjectMapper());
        executor = new LlmCallExecutor(chatModel, streamingChatModel, realQuota);
        when(chatModel.chat(request)).thenThrow(new RuntimeException("synthetic failure"));

        LlmCallExecutor.LlmCallException first = assertThrows(
                LlmCallExecutor.LlmCallException.class,
                () -> executor.executeBlocking(request, "trace-first"));
        LlmCallExecutor.LlmCallException second = assertThrows(
                LlmCallExecutor.LlmCallException.class,
                () -> executor.executeBlocking(request, "trace-second"));

        assertEquals(LlmCallExecutor.ErrorCode.LLM_UNAVAILABLE, first.code());
        assertEquals(LlmCallExecutor.ErrorCode.QUOTA_EXHAUSTED, second.code());
        verify(chatModel, times(1)).chat(request);
        LlmQuotaService.QuotaRecord record = new ObjectMapper().readValue(
                stateFile.toFile(), LlmQuotaService.QuotaRecord.class);
        assertEquals(1, record.count());
    }

    @Test
    void logsDoNotContainRequestOrProviderSensitiveText() {
        Logger logger = (Logger) LoggerFactory.getLogger(LlmCallExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ChatRequest sensitiveRequest = ChatRequest.builder()
                .messages(UserMessage.from("question-marker-that-must-not-be-logged"))
                .build();
        when(chatModel.chat(sensitiveRequest)).thenThrow(
                new RuntimeException("provider-credential-marker-that-must-not-be-logged"));

        try {
            assertThrows(
                    LlmCallExecutor.LlmCallException.class,
                    () -> executor.executeBlocking(sensitiveRequest, "trace-safe-log"));
            String messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", String::concat);
            assertFalse(messages.contains("question-marker-that-must-not-be-logged"));
            assertFalse(messages.contains("provider-credential-marker-that-must-not-be-logged"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static Stream<Arguments> blockingErrors() {
        return Stream.of(
                Arguments.of(
                        new AuthenticationException("provider-body-credential-marker"),
                        LlmCallExecutor.ErrorCode.LLM_UPSTREAM_REJECTED),
                Arguments.of(
                        new HttpException(403, "provider-body-credential-marker"),
                        LlmCallExecutor.ErrorCode.LLM_UPSTREAM_REJECTED),
                Arguments.of(
                        new InvalidRequestException("provider-body-credential-marker"),
                        LlmCallExecutor.ErrorCode.LLM_PROTOCOL_ERROR),
                Arguments.of(
                        new ModelNotFoundException("provider-body-credential-marker"),
                        LlmCallExecutor.ErrorCode.LLM_PROTOCOL_ERROR),
                Arguments.of(
                        new HttpException(404, "provider-body-credential-marker"),
                        LlmCallExecutor.ErrorCode.LLM_PROTOCOL_ERROR),
                Arguments.of(
                        new RateLimitException("provider-body-credential-marker"),
                        LlmCallExecutor.ErrorCode.LLM_UPSTREAM_RATE_LIMITED),
                Arguments.of(
                        new HttpException(429, "provider-body-credential-marker"),
                        LlmCallExecutor.ErrorCode.LLM_UPSTREAM_RATE_LIMITED),
                Arguments.of(
                        new TimeoutException("provider-body-credential-marker"),
                        LlmCallExecutor.ErrorCode.LLM_TIMEOUT),
                Arguments.of(
                        new RuntimeException("provider-body-credential-marker"),
                        LlmCallExecutor.ErrorCode.LLM_UNAVAILABLE));
    }
}
