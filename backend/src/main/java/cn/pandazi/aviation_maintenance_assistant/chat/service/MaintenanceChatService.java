package cn.pandazi.aviation_maintenance_assistant.chat.service;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.chat.dto.Citation;
import cn.pandazi.aviation_maintenance_assistant.chat.routing.AircraftInfoExtractor;
import cn.pandazi.aviation_maintenance_assistant.chat.routing.TopicClassifier;
import cn.pandazi.aviation_maintenance_assistant.chat.session.ChatSessionStore;
import cn.pandazi.aviation_maintenance_assistant.chat.session.SessionContext;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievalService;
import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import cn.pandazi.aviation_maintenance_assistant.validation.dto.ValidationResult;
import cn.pandazi.aviation_maintenance_assistant.validation.service.AircraftValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MaintenanceChatService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceChatService.class);

    private final AircraftValidationService validationService;
    private final ChatSessionStore sessionStore;
    private final GroundedChatService groundedChatService;

    public MaintenanceChatService(
            AircraftValidationService validationService,
            ChatSessionStore sessionStore,
            GroundedChatService groundedChatService) {
        this.validationService = validationService;
        this.sessionStore = sessionStore;
        this.groundedChatService = groundedChatService;
    }

    public ChatResponse process(String conversationId, String message) {
        Prepared prepared = prepare(conversationId, message);
        if (prepared.shortcut() != null) {
            return prepared.shortcut();
        }

        GroundedChatService.GroundedResult result = groundedChatService.respond(prepared.groundedRequest());
        return response(prepared, result);
    }

    public void processStream(String conversationId, String message, SseEmitter emitter) {
        Prepared prepared;
        try {
            prepared = prepare(conversationId, message);
        } catch (RuntimeException exception) {
            emitError(exception, conversationId, UUID.randomUUID().toString(), emitter);
            return;
        }

        AtomicBoolean terminated = new AtomicBoolean();
        if (prepared.shortcut() != null) {
            ChatResponse shortcut = prepared.shortcut();
            emitToken(shortcut.reply(), terminated, emitter);
            emitComplete(
                    prepared,
                    new GroundedChatService.GroundedResult(
                            null,
                            shortcut.outcome(),
                            shortcut.generationMode(),
                            shortcut.degraded(),
                            shortcut.degradationReason(),
                            shortcut.citations()),
                    terminated,
                    emitter);
            return;
        }

        try {
            groundedChatService.respondStream(
                    prepared.groundedRequest(),
                    new GroundedChatService.StreamObserver() {
                        @Override
                        public void onToken(String token) {
                            emitToken(token, terminated, emitter);
                        }

                        @Override
                        public void onComplete(GroundedChatService.GroundedResult result) {
                            emitComplete(prepared, result, terminated, emitter);
                        }

                        @Override
                        public void onError(Throwable error) {
                            emitError(error, prepared.conversationId(), prepared.traceId(), emitter, terminated);
                        }
                    });
        } catch (RuntimeException exception) {
            emitError(exception, prepared.conversationId(), prepared.traceId(), emitter, terminated);
        }
    }

    private Prepared prepare(String conversationId, String message) {
        String effectiveConversationId = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString()
                : conversationId;
        String traceId = UUID.randomUUID().toString();

        if (!TopicClassifier.needsAircraftInfo(message)) {
            return grounded(effectiveConversationId, traceId, message);
        }

        SessionContext session = sessionStore.get(effectiveConversationId);
        AircraftInfoExtractor.ExtractedAircraftInfo extracted = AircraftInfoExtractor.extract(message);
        String model = extracted.model() != null
                ? extracted.model()
                : validatedModel(session);
        String engine = extracted.engine() != null
                ? extracted.engine()
                : validatedEngine(session);

        if (model == null || engine == null) {
            return shortcut(
                    effectiveConversationId,
                    traceId,
                    "为了提供准确的维修信息，请提供机型和发动机型号。",
                    ChatResponse.Outcome.CLARIFICATION_REQUIRED);
        }

        ValidationResult validation = validationService.validate(model, engine);
        if (validation == ValidationResult.UNKNOWN) {
            return shortcut(
                    effectiveConversationId,
                    traceId,
                    "机型或发动机暂未收录，请核实型号并查阅官方维修手册。",
                    ChatResponse.Outcome.VALIDATION_UNKNOWN);
        }
        if (validation == ValidationResult.MISMATCH) {
            return shortcut(
                    effectiveConversationId,
                    traceId,
                    "机型与发动机型号不匹配，请核实后重新提供。",
                    ChatResponse.Outcome.VALIDATION_BLOCKED);
        }

        SessionContext validated = new SessionContext(model, engine, true);
        sessionStore.put(effectiveConversationId, validated);
        String contextualMessage = "[Current aircraft: " + model + "; engine: " + engine + "] " + message;
        return grounded(effectiveConversationId, traceId, contextualMessage);
    }

    private Prepared grounded(String conversationId, String traceId, String message) {
        return new Prepared(
                conversationId,
                traceId,
                null,
                new GroundedChatService.GroundedRequest(message, AviationPrompt.SYSTEM_PROMPT, traceId));
    }

    private Prepared shortcut(
            String conversationId, String traceId, String reply, ChatResponse.Outcome outcome) {
        return new Prepared(
                conversationId,
                traceId,
                new ChatResponse(
                        reply,
                        conversationId,
                        outcome,
                        traceId,
                        ChatResponse.GenerationMode.NONE,
                        false,
                        null,
                        List.of()),
                null);
    }

    private ChatResponse response(Prepared prepared, GroundedChatService.GroundedResult result) {
        log.info("Chat request completed: traceId={}, outcome={}", prepared.traceId(), result.outcome());
        return new ChatResponse(
                result.reply(),
                prepared.conversationId(),
                result.outcome(),
                prepared.traceId(),
                result.generationMode(),
                result.degraded(),
                result.degradationReason(),
                result.citations());
    }

    private void emitToken(String token, AtomicBoolean terminated, SseEmitter emitter) {
        if (terminated.get()) {
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("token").data(token, MediaType.TEXT_PLAIN));
            }
        } catch (IOException exception) {
            terminated.set(true);
            emitter.completeWithError(exception);
        }
    }

    private void emitComplete(
            Prepared prepared,
            GroundedChatService.GroundedResult result,
            AtomicBoolean terminated,
            SseEmitter emitter) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("complete").data(
                        new StreamComplete(
                                prepared.conversationId(),
                                result.outcome(),
                                prepared.traceId(),
                                result.generationMode(),
                                result.degraded(),
                                result.degradationReason(),
                                result.citations()),
                        MediaType.APPLICATION_JSON));
            }
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private void emitError(
            Throwable error,
            String conversationId,
            String traceId,
            SseEmitter emitter) {
        emitError(error, conversationId, traceId, emitter, new AtomicBoolean());
    }

    private void emitError(
            Throwable error,
            String conversationId,
            String traceId,
            SseEmitter emitter,
            AtomicBoolean terminated) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }

        String code = "INTERNAL_ERROR";
        String message = "The request could not be completed.";
        String effectiveTraceId = traceId;
        if (error instanceof LlmCallExecutor.LlmCallException exception) {
            code = exception.code().name();
            message = exception.getMessage();
            effectiveTraceId = exception.traceId();
        } else if (error instanceof RetrievalService.RetrievalException exception) {
            code = exception.code();
            message = exception.getMessage();
            effectiveTraceId = exception.traceId();
        }

        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name("error").data(
                        new StreamError(code, message, effectiveTraceId), MediaType.APPLICATION_JSON));
            }
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private String validatedModel(SessionContext session) {
        return session != null && session.validated() ? session.confirmedModel() : null;
    }

    private String validatedEngine(SessionContext session) {
        return session != null && session.validated() ? session.confirmedEngine() : null;
    }

    private record Prepared(
            String conversationId,
            String traceId,
            ChatResponse shortcut,
            GroundedChatService.GroundedRequest groundedRequest) {
    }

    private record StreamComplete(
            String conversationId,
            ChatResponse.Outcome outcome,
            String traceId,
            ChatResponse.GenerationMode generationMode,
            boolean degraded,
            String degradationReason,
            List<Citation> citations) {
    }

    private record StreamError(String code, String message, String traceId) {
    }
}
