package cn.pandazi.aviation_maintenance_assistant.document.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentIdentityTest {

    private static final byte[] RAW_BYTES =
            "Synthetic maintenance identity vector\n".getBytes(UTF_8);
    private static final String SOURCE_SHA256 =
            "48131d724567c394eb6f99d35b29e62fca30c24b2c9fd8134b55cdfcc5d2b418";
    private static final String DOCUMENT_ID = "doc_" + SOURCE_SHA256;
    private static final String CHUNK_TEXT = "Synthetic chunk text.\nSecond line.";
    private static final String EXCERPT_ID =
            "exr_f340097f8ff189dccad9d2157138d4d9b70bb2caffda29bdce6b9619837898d0";
    private static final Instant INGESTED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void documentIdentityShouldMatchIndependentHashVector() {
        assertThat(DocumentIdentity.sourceSha256(RAW_BYTES)).isEqualTo(SOURCE_SHA256);
        assertThat(DocumentIdentity.documentId(RAW_BYTES)).isEqualTo(DOCUMENT_ID);
        assertThat(DocumentIdentity.sourceSha256(RAW_BYTES)).hasSize(64);
        assertThat(DocumentIdentity.documentId(RAW_BYTES))
                .isEqualTo(DocumentIdentity.documentId(RAW_BYTES.clone()));
    }

    @Test
    void excerptIdentityShouldMatchIndependentHashVector() {
        assertThat(DocumentIdentity.excerptId(DOCUMENT_ID, 0, CHUNK_TEXT))
                .isEqualTo(EXCERPT_ID)
                .hasSize(68);
        assertThat(DocumentIdentity.excerptId(DOCUMENT_ID, 0, CHUNK_TEXT))
                .isEqualTo(DocumentIdentity.excerptId(DOCUMENT_ID, 0, CHUNK_TEXT));
    }

    @Test
    void changedBytesShouldChangeDocumentIdentity() {
        byte[] changedBytes = RAW_BYTES.clone();
        changedBytes[0] ^= 1;

        assertThat(DocumentIdentity.documentId(changedBytes)).isNotEqualTo(DOCUMENT_ID);
    }

    @Test
    void chunkIndexAndExactTextShouldAffectExcerptIdentity() {
        String repeatedTextId = DocumentIdentity.excerptId(DOCUMENT_ID, 1, CHUNK_TEXT);

        assertThat(repeatedTextId).isNotEqualTo(EXCERPT_ID);
        assertThat(DocumentIdentity.excerptId(DOCUMENT_ID, 0, CHUNK_TEXT + " "))
                .isNotEqualTo(EXCERPT_ID);
        assertThatThrownBy(() -> DocumentIdentity.excerptId(DOCUMENT_ID, -1, CHUNK_TEXT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void baseMetadataShouldContainOnlyFixedDocumentFields() {
        Metadata metadata = DocumentIdentity.baseMetadata(
                "sample.md", "text/markdown", RAW_BYTES, INGESTED_AT);

        assertThat(metadata.toMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "document_id", DOCUMENT_ID,
                "document_title", "sample.md",
                "source_name", "sample.md",
                "source_sha256", SOURCE_SHA256,
                "media_type", "text/markdown",
                "chunker_version", DocumentIdentity.CHUNKER_VERSION,
                "ingested_at", INGESTED_AT.toString()));
    }

    @Test
    void excerptMetadataShouldCopyBaseAndAddStableExcerptFields() {
        Metadata base = DocumentIdentity.baseMetadata(
                "sample.md", "text/markdown", RAW_BYTES, INGESTED_AT);
        Map<String, Object> originalBase = Map.copyOf(base.toMap());

        Metadata excerpt = DocumentIdentity.excerptMetadata(base, 0, CHUNK_TEXT);

        assertThat(base.toMap()).isEqualTo(originalBase);
        assertThat(excerpt.toMap())
                .containsAllEntriesOf(originalBase)
                .containsEntry("excerpt_id", EXCERPT_ID)
                .containsEntry("chunk_index", 0)
                .hasSize(9);
        assertThat(excerpt.toMap()).doesNotContainKeys("page", "section", "score");
    }

    @Test
    void chunkerShouldUseFrozenSettingsAndReproduceExcerptIds() {
        String sourceText = "Synthetic maintenance text for deterministic chunking. ".repeat(80);
        Document document = Document.from(sourceText);

        List<TextSegment> actual = DocumentIdentity.newChunker().split(document);
        List<TextSegment> expected = DocumentSplitters.recursive(500, 50).split(document);
        List<TextSegment> repeated = DocumentIdentity.newChunker().split(document);

        assertThat(actual).hasSizeGreaterThan(1);
        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(actual).containsExactlyElementsOf(repeated);
        assertThat(excerptIds(actual)).containsExactlyElementsOf(excerptIds(repeated));
    }

    private static List<String> excerptIds(List<TextSegment> segments) {
        return IntStream.range(0, segments.size())
                .mapToObj(index -> DocumentIdentity.excerptId(
                        DOCUMENT_ID, index, segments.get(index).text()))
                .toList();
    }
}
