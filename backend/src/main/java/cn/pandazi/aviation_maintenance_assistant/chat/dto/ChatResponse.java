package cn.pandazi.aviation_maintenance_assistant.chat.dto;

import java.util.List;

public record ChatResponse(
        String reply,
        String conversationId,
        Outcome outcome,
        String traceId,
        GenerationMode generationMode,
        boolean degraded,
        String degradationReason,
        List<Citation> citations) {

    public enum Outcome {
        ANSWERED,
        NO_ANSWER,
        CLARIFICATION_REQUIRED,
        VALIDATION_BLOCKED,
        VALIDATION_UNKNOWN,
        QUOTA_EXHAUSTED
    }

    public enum GenerationMode {
        STREAMING,
        NON_STREAMING,
        NONE
    }

    public ChatResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
