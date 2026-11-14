package cn.pandazi.aviation_maintenance_assistant.document.service;

import cn.pandazi.aviation_maintenance_assistant.config.AmaProperties;
import cn.pandazi.aviation_maintenance_assistant.document.dto.IngestionReport;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final SourceDocumentParser parser;
    private final AmaProperties properties;

    public DocumentIngestionService(
            @Lazy EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            SourceDocumentParser parser,
            AmaProperties properties) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.parser = parser;
        this.properties = properties;
    }

    public IngestionReport ingestDirectory() {
        Path dataDirectory = Path.of(properties.getDataPath());
        if (!Files.isDirectory(dataDirectory) || !Files.isReadable(dataDirectory)) {
            throw new IngestionInfrastructureException("INGESTION_SOURCE_UNAVAILABLE");
        }

        List<Path> sources;
        try (Stream<Path> paths = Files.list(dataDirectory)) {
            sources = paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new IngestionInfrastructureException("INGESTION_SOURCE_UNAVAILABLE");
        }

        List<IngestionReport.DocumentResult> results = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        Map<String, String> firstSourceByDocument = new HashMap<>();
        int failures = 0;
        int upserts = 0;

        for (Path source : sources) {
            String sourceName = source.getFileName().toString();
            if (!parser.supports(source)) {
                ignored.add(sourceName);
                continue;
            }

            try {
                Document document = parser.parse(source);
                String documentId = document.metadata().getString("document_id");
                String duplicateOf = firstSourceByDocument.get(documentId);
                if (duplicateOf != null) {
                    results.add(result(sourceName, "DUPLICATE_CONTENT", documentId, null, duplicateOf, null));
                    continue;
                }
                firstSourceByDocument.put(documentId, sourceName);

                int excerptCount = upsert(document);
                results.add(result(sourceName, "UPSERTED", documentId, excerptCount, null, null));
                upserts++;
            } catch (IOException | SourceDocumentParser.SourceDocumentParseException exception) {
                failures++;
                results.add(result(sourceName, "FAILED", null, null, null, "DOCUMENT_PARSE_FAILED"));
                log.warn("Document ingestion failed: sourceName={}, errorCode={}", sourceName, "DOCUMENT_PARSE_FAILED");
            } catch (RuntimeException exception) {
                failures++;
                results.add(result(sourceName, "FAILED", null, null, null, "DOCUMENT_WRITE_FAILED"));
                log.warn("Document ingestion failed: sourceName={}, errorCode={}, exceptionType={}",
                        sourceName, "DOCUMENT_WRITE_FAILED", exception.getClass().getSimpleName());
            }
        }

        if (results.isEmpty()) {
            throw new IngestionInfrastructureException("INGESTION_SOURCE_EMPTY");
        }

        String status = failures == 0
                ? "COMPLETED"
                : upserts == 0 ? "FAILED" : "COMPLETED_WITH_ERRORS";
        return new IngestionReport(status, properties.getChroma().getCollection(), results, ignored);
    }

    private int upsert(Document document) {
        List<TextSegment> splitSegments = DocumentIdentity.newChunker().split(document);
        if (splitSegments.isEmpty()) {
            throw new IllegalStateException("Document contains no chunks");
        }

        List<TextSegment> segments = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < splitSegments.size(); index++) {
            String text = splitSegments.get(index).text();
            var metadata = DocumentIdentity.excerptMetadata(document.metadata(), index, text);
            segments.add(TextSegment.from(text, metadata));
            ids.add(metadata.getString("excerpt_id"));
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        if (embeddings.size() != segments.size()) {
            throw new IllegalStateException("Embedding result count mismatch");
        }

        String documentId = document.metadata().getString("document_id");
        embeddingStore.removeAll(new IsEqualTo("document_id", documentId));
        embeddingStore.addAll(ids, embeddings, segments);
        return segments.size();
    }

    private IngestionReport.DocumentResult result(
            String sourceName,
            String status,
            String documentId,
            Integer excerptCount,
            String duplicateOf,
            String errorCode) {
        return new IngestionReport.DocumentResult(
                sourceName, status, documentId, excerptCount, duplicateOf, errorCode);
    }

    public static class IngestionInfrastructureException extends RuntimeException {
        private final String code;

        private IngestionInfrastructureException(String code) {
            super(safeMessage(code));
            this.code = code;
        }

        public String code() {
            return code;
        }

        private static String safeMessage(String code) {
            return "INGESTION_SOURCE_EMPTY".equals(code)
                    ? "The ingestion directory contains no supported documents."
                    : "The ingestion directory is unavailable.";
        }
    }
}
