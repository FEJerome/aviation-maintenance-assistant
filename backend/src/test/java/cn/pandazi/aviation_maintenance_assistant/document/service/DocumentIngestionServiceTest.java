package cn.pandazi.aviation_maintenance_assistant.document.service;

import cn.pandazi.aviation_maintenance_assistant.config.AmaProperties;
import cn.pandazi.aviation_maintenance_assistant.document.dto.IngestionReport;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceTest {

    @TempDir
    Path dataDirectory;

    private RecordingStore store;
    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        store = new RecordingStore();
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            List<Embedding> embeddings = new ArrayList<>();
            for (int index = 0; index < segments.size(); index++) {
                embeddings.add(Embedding.from(new float[]{0.1f, 0.2f}));
            }
            return Response.from(embeddings);
        });
        AmaProperties properties = new AmaProperties();
        properties.setDataPath(dataDirectory.toString());
        properties.getChroma().setCollection("synthetic-test-documents");
        SourceDocumentParser parser = new SourceDocumentParser(
                new dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser(),
                Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));
        service = new DocumentIngestionService(store, embeddingModel, parser, properties);
    }

    @Test
    void repeatedIngestionIsIdempotentAndAliasesAreReported() throws Exception {
        Files.writeString(dataDirectory.resolve("a.md"), "Synthetic maintenance text. ".repeat(80));
        Files.writeString(dataDirectory.resolve("b.markdown"), "Synthetic maintenance text. ".repeat(80));
        Files.writeString(dataDirectory.resolve("notes.txt"), "Ignored synthetic note");

        IngestionReport first = service.ingestDirectory();
        Map<String, TextSegment> firstIds = Map.copyOf(store.entries);
        IngestionReport second = service.ingestDirectory();

        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(first.documents()).extracting(IngestionReport.DocumentResult::status)
                .containsExactly("UPSERTED", "DUPLICATE_CONTENT");
        assertThat(first.ignored()).containsExactly("notes.txt");
        assertThat(second.status()).isEqualTo("COMPLETED");
        assertThat(store.entries).hasSameSizeAs(firstIds);
        assertThat(store.entries.keySet()).containsExactlyInAnyOrderElementsOf(firstIds.keySet());
        assertThat(store.entries.values()).allSatisfy(segment -> {
            assertThat(segment.metadata().getString("excerpt_id")).isNotBlank();
            assertThat(segment.metadata().getString("document_id")).isNotBlank();
            assertThat(segment.metadata().toString()).doesNotContain(dataDirectory.toString());
        });
    }

    @Test
    void changedBytesCreateANewVersionWithoutDeletingTheOldOne() throws Exception {
        Path source = dataDirectory.resolve("sample.md");
        Files.writeString(source, "First synthetic version");
        service.ingestDirectory();
        Map<String, TextSegment> oldEntries = Map.copyOf(store.entries);

        Files.writeString(source, "Second synthetic version with changed bytes");
        service.ingestDirectory();

        assertThat(store.entries.keySet()).containsAll(oldEntries.keySet());
        assertThat(store.entries.size()).isGreaterThan(oldEntries.size());
    }

    @Test
    void partialWriteFailureConvergesOnRetry() throws Exception {
        Files.writeString(dataDirectory.resolve("sample.md"), "Synthetic chunk text. ".repeat(100));
        store.failNextAdd = true;

        IngestionReport failed = service.ingestDirectory();
        IngestionReport retried = service.ingestDirectory();

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.documents().get(0).errorCode()).isEqualTo("DOCUMENT_WRITE_FAILED");
        assertThat(retried.status()).isEqualTo("COMPLETED");
        assertThat(store.entries).isNotEmpty();
        assertThat(store.entries.size()).isEqualTo(retried.documents().get(0).excerptCount());
    }

    @Test
    void emptyDirectoryFailsBeforeWriting() {
        assertThatThrownBy(() -> service.ingestDirectory())
                .isInstanceOf(DocumentIngestionService.IngestionInfrastructureException.class)
                .extracting("code")
                .isEqualTo("INGESTION_SOURCE_EMPTY");
        assertThat(store.entries).isEmpty();
    }

    private static final class RecordingStore implements EmbeddingStore<TextSegment> {
        private final Map<String, TextSegment> entries = new LinkedHashMap<>();
        private boolean failNextAdd;

        @Override
        public String add(Embedding embedding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(String id, Embedding embedding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String add(Embedding embedding, TextSegment segment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
            for (int index = 0; index < ids.size(); index++) {
                entries.put(ids.get(index), segments.get(index));
                if (failNextAdd) {
                    failNextAdd = false;
                    throw new IllegalStateException("Synthetic partial write");
                }
            }
        }

        @Override
        public void removeAll(Filter filter) {
            IsEqualTo comparison = (IsEqualTo) filter;
            Iterator<Map.Entry<String, TextSegment>> iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, TextSegment> entry = iterator.next();
                Object actual = entry.getValue().metadata().toMap().get(comparison.key());
                if (comparison.comparisonValue().equals(actual)) {
                    iterator.remove();
                }
            }
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            List<EmbeddingMatch<TextSegment>> matches = entries.entrySet().stream()
                    .map(entry -> new EmbeddingMatch<>(1.0, entry.getKey(), null, entry.getValue()))
                    .toList();
            return new EmbeddingSearchResult<>(matches);
        }
    }
}
