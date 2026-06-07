package cn.pandazi.aviation_maintenance_assistant.chat.service;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.chat.routing.AircraftInfoExtractor;
import cn.pandazi.aviation_maintenance_assistant.chat.routing.TopicClassifier;
import cn.pandazi.aviation_maintenance_assistant.chat.session.ChatSessionStore;
import cn.pandazi.aviation_maintenance_assistant.chat.session.SessionContext;
import cn.pandazi.aviation_maintenance_assistant.validation.dto.ValidationResult;
import cn.pandazi.aviation_maintenance_assistant.validation.service.AircraftValidationService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/**
 * 机务聊天路由层核心服务
 * <p>
 * 负责在问题进入 RAG 链路之前进行：
 * 1. 会话状态检查
 * 2. 路由分类（关键系统 vs 通用知识）
 * 3. 机型信息提取与验证
 * 4. 机型上下文注入
 * <p>
 * 所有控制流决策均为确定性代码，不依赖 LLM 判断。
 */
@Service
public class MaintenanceChatService {

    private final Assistant assistant;
    private final AircraftValidationService validationService;
    private final ChatSessionStore sessionStore;

    public MaintenanceChatService(Assistant assistant,
                                  AircraftValidationService validationService,
                                  ChatSessionStore sessionStore) {
        this.assistant = assistant;
        this.validationService = validationService;
        this.sessionStore = sessionStore;
    }

    /**
     * 处理用户聊天请求（阻塞式）
     *
     * @param conversationId 会话 ID，可为 null（首次请求）
     * @param message        用户原始问题
     * @return 包含回复和 conversationId 的响应
     */
    public ChatResponse process(String conversationId, String message) {
        // 会话初始化：为空则生成新 ID
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        SessionContext session = sessionStore.get(conversationId);

        // 已验证会话：直接基于机型上下文走 RAG
        if (session != null && session.validated()) {
            String contextualMessage = buildContextualMessage(session, message);
            String reply = assistant.chat(contextualMessage);
            return new ChatResponse(reply, conversationId);
        }

        // 路由分类：是否涉及关键系统
        boolean needsAircraft = TopicClassifier.needsAircraftInfo(message);
        if (!needsAircraft) {
            String reply = assistant.chat(message);
            return new ChatResponse(reply, conversationId);
        }

        // 提取机型和发动机
        AircraftInfoExtractor.ExtractedAircraftInfo info = AircraftInfoExtractor.extract(message);
        if (!info.isComplete()) {
            String reply = "为了提供准确的维修信息，请提供机型（如 B737-800）和发动机型号（如 CFM56-7B）。";
            return new ChatResponse(reply, conversationId);
        }

        // 验证机型-发动机匹配
        ValidationResult result = validationService.validate(info.model(), info.engine());

        return switch (result) {
            case MATCH -> {
                SessionContext newSession = new SessionContext(info.model(), info.engine(), true);
                sessionStore.put(conversationId, newSession);
                String contextualMessage = buildContextualMessage(newSession, message);
                String reply = assistant.chat(contextualMessage);
                yield new ChatResponse(reply, conversationId);
            }
            case MISMATCH -> {
                String reply = String.format(
                        "机型 %s 与发动机 %s 不匹配。请核实后重新提供。",
                        info.model(), info.engine()
                );
                yield new ChatResponse(reply, conversationId);
            }
            case UNKNOWN -> {
                String reply = String.format(
                        "机型 %s 或发动机 %s 暂未在系统中收录。当前仅支持常见机型组合，具体程序请查阅官方 AMM。",
                        info.model(), info.engine()
                );
                yield new ChatResponse(reply, conversationId);
            }
        };
    }

    /**
     * 处理用户聊天请求（流式）
     * <p>
     * 路由、验证、会话逻辑与 {@link #process} 完全一致，仅在 LLM 调用环节使用流式输出。
     *
     * @param conversationId 会话 ID，可为 null（首次请求）
     * @param message        用户原始问题
     * @param emitter        SSE 发射器，用于向客户端推送 token
     */
    public void processStream(String conversationId, String message, SseEmitter emitter) {
        try {
            // 会话初始化：为空则生成新 ID
            if (conversationId == null || conversationId.isBlank()) {
                conversationId = UUID.randomUUID().toString();
            }

            final String finalConversationId = conversationId;
            SessionContext session = sessionStore.get(conversationId);

            // 已验证会话：直接基于机型上下文走流式 RAG
            if (session != null && session.validated()) {
                String contextualMessage = buildContextualMessage(session, message);
                streamAssistantReply(contextualMessage, finalConversationId, emitter);
                return;
            }

            // 路由分类：是否涉及关键系统
            boolean needsAircraft = TopicClassifier.needsAircraftInfo(message);
            if (!needsAircraft) {
                streamAssistantReply(message, finalConversationId, emitter);
                return;
            }

            // 提取机型和发动机
            AircraftInfoExtractor.ExtractedAircraftInfo info = AircraftInfoExtractor.extract(message);
            if (!info.isComplete()) {
                String reply = "为了提供准确的维修信息，请提供机型（如 B737-800）和发动机型号（如 CFM56-7B）。";
                emitComplete(reply, finalConversationId, emitter);
                return;
            }

            // 验证机型-发动机匹配
            ValidationResult result = validationService.validate(info.model(), info.engine());

            switch (result) {
                case MATCH -> {
                    SessionContext newSession = new SessionContext(info.model(), info.engine(), true);
                    sessionStore.put(finalConversationId, newSession);
                    String contextualMessage = buildContextualMessage(newSession, message);
                    streamAssistantReply(contextualMessage, finalConversationId, emitter);
                }
                case MISMATCH -> {
                    String reply = String.format(
                            "机型 %s 与发动机 %s 不匹配。请核实后重新提供。",
                            info.model(), info.engine()
                    );
                    emitComplete(reply, finalConversationId, emitter);
                }
                case UNKNOWN -> {
                    String reply = String.format(
                            "机型 %s 或发动机 %s 暂未在系统中收录。当前仅支持常见机型组合，具体程序请查阅官方 AMM。",
                            info.model(), info.engine()
                    );
                    emitComplete(reply, finalConversationId, emitter);
                }
            }
        } catch (Exception e) {
            emitError("Internal error: " + e.getMessage(), emitter);
        }
    }

    /**
     * 调用 Assistant 流式接口，将 token 推送到 SSE
     */
    private void streamAssistantReply(String message, String conversationId, SseEmitter emitter) {
        try {
            assistant.chatStream(message)
                    .onPartialResponse(token -> {
                        try {
                            emitter.send(SseEmitter.event().name("token").data(token));
                        } catch (IOException e) {
                            // 客户端断开，静默处理
                        }
                    })
                    .onCompleteResponse(response -> emitComplete(null, conversationId, emitter))
                    .onError(error -> emitError("LLM error: " + error.getMessage(), emitter))
                    .start();
        } catch (Exception e) {
            emitError("Failed to start stream: " + e.getMessage(), emitter);
        }
    }

    /**
     * 发送完整文本（用于拦截/验证失败场景）并关闭连接
     */
    private void emitComplete(String reply, String conversationId, SseEmitter emitter) {
        try {
            if (reply != null && !reply.isBlank()) {
                emitter.send(SseEmitter.event().name("token").data(reply));
            }
            emitter.send(SseEmitter.event().name("complete")
                    .data("{\"conversationId\":\"" + conversationId + "\"}"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 发送 SSE 错误事件并关闭连接
     */
    private void emitError(String errorMessage, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("error").data(errorMessage));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private String buildContextualMessage(SessionContext session, String message) {
        return String.format("[当前机型：%s，发动机：%s] %s",
                session.confirmedModel(), session.confirmedEngine(), message);
    }
}
