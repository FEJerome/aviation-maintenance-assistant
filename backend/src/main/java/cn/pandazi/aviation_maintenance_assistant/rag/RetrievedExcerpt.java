package cn.pandazi.aviation_maintenance_assistant.rag;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.Citation;

public record RetrievedExcerpt(
        String text,
        String excerptId,
        String documentId,
        String documentTitle,
        int chunkIndex,
        double score) {

    public Citation toCitation() {
        return new Citation(excerptId, documentId, documentTitle, chunkIndex, score);
    }
}
