package cn.pandazi.aviation_maintenance_assistant.chat.api;

import cn.pandazi.aviation_maintenance_assistant.document.service.DocumentIngestionService;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievalService;
import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(LlmCallExecutor.LlmCallException.class)
    public ResponseEntity<ErrorResponse> handleLlm(LlmCallExecutor.LlmCallException exception) {
        HttpStatus status = switch (exception.code()) {
            case QUOTA_EXHAUSTED, LLM_UPSTREAM_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case LLM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case LLM_UPSTREAM_REJECTED, LLM_PROTOCOL_ERROR, LLM_STREAMING_UNSUPPORTED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return response(exception.code().name(), exception.getMessage(), exception.traceId(), status, exception);
    }

    @ExceptionHandler(RetrievalService.RetrievalException.class)
    public ResponseEntity<ErrorResponse> handleRetrieval(RetrievalService.RetrievalException exception) {
        HttpStatus status = "QUERY_TRANSFORM_FAILED".equals(exception.code())
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.SERVICE_UNAVAILABLE;
        return response(exception.code(), exception.getMessage(), exception.traceId(), status, exception);
    }

    @ExceptionHandler(DocumentIngestionService.IngestionInfrastructureException.class)
    public ResponseEntity<ErrorResponse> handleIngestion(
            DocumentIngestionService.IngestionInfrastructureException exception) {
        HttpStatus status = "INGESTION_SOURCE_EMPTY".equals(exception.code())
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.SERVICE_UNAVAILABLE;
        return response(exception.code(), exception.getMessage(), UUID.randomUUID().toString(), status, exception);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return response(
                "INVALID_REQUEST",
                "The request body is invalid.",
                UUID.randomUUID().toString(),
                HttpStatus.BAD_REQUEST,
                exception);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException exception) {
        return response(
                "NOT_FOUND",
                "The requested resource was not found.",
                UUID.randomUUID().toString(),
                HttpStatus.NOT_FOUND,
                exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception) {
        return response(
                "METHOD_NOT_ALLOWED",
                "The request method is not supported.",
                UUID.randomUUID().toString(),
                HttpStatus.METHOD_NOT_ALLOWED,
                exception);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception) {
        return response(
                "UNSUPPORTED_MEDIA_TYPE",
                "The request content type is not supported.",
                UUID.randomUUID().toString(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        return response(
                "INTERNAL_ERROR",
                "The request could not be completed.",
                UUID.randomUUID().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception);
    }

    private ResponseEntity<ErrorResponse> response(
            String code, String message, String traceId, HttpStatus status, Exception exception) {
        log.warn(
                "Request failed: traceId={}, code={}, exceptionType={}",
                traceId,
                code,
                exception.getClass().getSimpleName());
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, traceId));
    }

    public record ErrorResponse(String code, String message, String traceId) {
    }
}
