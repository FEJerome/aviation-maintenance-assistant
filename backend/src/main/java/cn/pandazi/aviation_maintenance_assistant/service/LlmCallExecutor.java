package cn.pandazi.aviation_maintenance_assistant.service;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * The shared model invocation boundary. Every invocation reserves exactly one quota attempt.
 */
@Service
public class LlmCallExecutor {

    public enum ErrorCode {
        QUOTA_EXHAUSTED,
        QUOTA_STATE_UNAVAILABLE,
        LLM_UPSTREAM_REJECTED,
        LLM_UPSTREAM_RATE_LIMITED,
        LLM_PROTOCOL_ERROR,
        LLM_STREAMING_UNSUPPORTED,
        LLM_STREAM_INTERRUPTED,
        LLM_TIMEOUT,
        LLM_UNAVAILABLE
    }

    private static final Logger log = LoggerFactory.getLogger(LlmCallExecutor.class);

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final LlmQuotaService quotaService;

    public LlmCallExecutor(
            ChatModel chatModel,
            StreamingChatModel streamingChatModel,
            LlmQuotaService quotaService) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.quotaService = quotaService;
    }

    public ChatResponse executeBlocking(ChatRequest request, String traceId) {
        Objects.requireNonNull(request, "request");
        acquire(traceId);
        try {
            return chatModel.chat(request);
        } catch (RuntimeException exception) {
            throw normalize(exception, false, traceId);
        }
    }

    /**
     * Starts one streaming invocation. Synchronous failures are thrown; asynchronous failures
     * are delivered to the supplied handler. No alternate model invocation is attempted.
     */
    public void executeStreaming(
            ChatRequest request,
            String traceId,
            StreamingChatResponseHandler downstream) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(downstream, "downstream");
        acquire(traceId);

        StreamingChatResponseHandler guardedHandler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                downstream.onPartialResponse(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                downstream.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                downstream.onError(normalize(error, true, traceId));
            }
        };

        try {
            streamingChatModel.chat(request, guardedHandler);
        } catch (RuntimeException exception) {
            throw normalize(exception, true, traceId);
        }
    }

    private void acquire(String traceId) {
        try {
            quotaService.acquire();
        } catch (LlmQuotaService.QuotaExhaustedException exception) {
            throw failure(ErrorCode.QUOTA_EXHAUSTED, traceId, exception);
        } catch (LlmQuotaService.QuotaStateUnavailableException exception) {
            throw failure(ErrorCode.QUOTA_STATE_UNAVAILABLE, traceId, exception);
        }
    }

    private LlmCallException normalize(Throwable error, boolean streaming, String traceId) {
        ErrorCode code;
        if (error instanceof TimeoutException) {
            code = ErrorCode.LLM_TIMEOUT;
        } else if (error instanceof AuthenticationException || hasHttpStatus(error, 401, 403)) {
            code = ErrorCode.LLM_UPSTREAM_REJECTED;
        } else if (error instanceof RateLimitException || hasHttpStatus(error, 429)) {
            code = ErrorCode.LLM_UPSTREAM_RATE_LIMITED;
        } else if (isProtocolIncompatibility(error)) {
            code = streaming
                    ? ErrorCode.LLM_STREAMING_UNSUPPORTED
                    : ErrorCode.LLM_PROTOCOL_ERROR;
        } else {
            code = streaming ? ErrorCode.LLM_STREAM_INTERRUPTED : ErrorCode.LLM_UNAVAILABLE;
        }
        return failure(code, traceId, error);
    }

    private boolean isProtocolIncompatibility(Throwable error) {
        return error instanceof InvalidRequestException
                || error instanceof ModelNotFoundException
                || error instanceof UnsupportedFeatureException
                || hasHttpStatus(error, 400, 404);
    }

    private boolean hasHttpStatus(Throwable error, int... expectedStatuses) {
        if (!(error instanceof HttpException httpException)) {
            return false;
        }
        for (int expectedStatus : expectedStatuses) {
            if (httpException.statusCode() == expectedStatus) {
                return true;
            }
        }
        return false;
    }

    private LlmCallException failure(ErrorCode code, String traceId, Throwable error) {
        Integer httpStatus = error instanceof HttpException httpException
                ? httpException.statusCode()
                : null;
        log.warn(
                "Model invocation failed: traceId={}, code={}, exceptionType={}, httpStatus={}",
                safeTraceId(traceId),
                code,
                error.getClass().getSimpleName(),
                httpStatus);
        return new LlmCallException(code, safeTraceId(traceId));
    }

    private String safeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return "unknown";
        }
        String sanitized = traceId.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 128 ? sanitized : sanitized.substring(0, 128);
    }

    public static class LlmCallException extends RuntimeException {

        private final ErrorCode code;
        private final String traceId;

        private LlmCallException(ErrorCode code, String traceId) {
            super(safeMessage(code));
            this.code = code;
            this.traceId = traceId;
        }

        public ErrorCode code() {
            return code;
        }

        public String traceId() {
            return traceId;
        }

        private static String safeMessage(ErrorCode code) {
            return switch (code) {
                case QUOTA_EXHAUSTED -> "Daily LLM quota is exhausted.";
                case QUOTA_STATE_UNAVAILABLE -> "LLM quota state is unavailable.";
                case LLM_UPSTREAM_REJECTED -> "The configured LLM endpoint rejected the request.";
                case LLM_UPSTREAM_RATE_LIMITED -> "The configured LLM endpoint rate limited the request.";
                case LLM_PROTOCOL_ERROR -> "The configured LLM endpoint returned a protocol error.";
                case LLM_STREAMING_UNSUPPORTED -> "The configured LLM endpoint does not support this streaming request.";
                case LLM_STREAM_INTERRUPTED -> "The LLM stream was interrupted.";
                case LLM_TIMEOUT -> "The configured LLM endpoint timed out.";
                case LLM_UNAVAILABLE -> "The configured LLM endpoint is unavailable.";
            };
        }
    }
}
