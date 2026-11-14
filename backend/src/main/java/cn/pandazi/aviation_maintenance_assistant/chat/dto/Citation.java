package cn.pandazi.aviation_maintenance_assistant.chat.dto;

public record Citation(
        String excerptId,
        String documentId,
        String documentTitle,
        int chunkIndex,
        double score) {
}
