package cn.pandazi.aviation_maintenance_assistant.document.dto;

import java.util.List;

public record IngestionReport(
        String status,
        String collection,
        List<DocumentResult> documents,
        List<String> ignored) {

    public IngestionReport {
        documents = documents == null ? List.of() : List.copyOf(documents);
        ignored = ignored == null ? List.of() : List.copyOf(ignored);
    }

    public record DocumentResult(
            String sourceName,
            String status,
            String documentId,
            Integer excerptCount,
            String duplicateOf,
            String errorCode) {
    }
}
