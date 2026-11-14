package cn.pandazi.aviation_maintenance_assistant.document.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class SourceDocumentParserTest {

    private static final Instant INGESTED_AT = Instant.parse("2026-01-02T03:04:05Z");
    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    @TempDir
    Path tempDir;

    private final Clock fixedClock = Clock.fixed(INGESTED_AT, ZoneOffset.UTC);
    private final SourceDocumentParser parser =
            new SourceDocumentParser(new ApacheTikaDocumentParser(), fixedClock);

    @Test
    void markdownShouldBeReadAsExactUtf8Text() throws IOException {
        Path fixture = FIXTURES.resolve("synthetic-sample.md");
        byte[] rawBytes = Files.readAllBytes(fixture);

        Document document = parser.parse(fixture);

        assertThat(document.text()).isEqualTo(new String(rawBytes, UTF_8));
        assertThat(document.text())
                .contains("# Synthetic Maintenance Training Note")
                .contains("| Item | Synthetic value |")
                .contains("中文说明");
        assertBaseMetadata(document.metadata(), "synthetic-sample.md", "text/markdown", rawBytes);
    }

    @Test
    void pdfShouldUseTikaAndRetainOnlyFixedMetadata() throws IOException {
        Path fixture = FIXTURES.resolve("synthetic-sample.pdf");
        byte[] rawBytes = Files.readAllBytes(fixture);

        Document document = parser.parse(fixture);

        assertThat(document.text())
                .contains("SYNTHETIC PDF TRAINING NOTE")
                .contains("Original parser test material only.")
                .contains("not an official maintenance manual");
        assertBaseMetadata(document.metadata(), "synthetic-sample.pdf", "application/pdf", rawBytes);
    }

    @Test
    void identicalBytesShouldKeepDocumentIdAcrossNamesAndExtensionCase() throws IOException {
        Path markdownFixture = FIXTURES.resolve("synthetic-sample.md");
        byte[] markdownBytes = Files.readAllBytes(markdownFixture);
        String markdownId = DocumentIdentity.documentId(markdownBytes);

        for (String sourceName : List.of("renamed.MD", "another.markdown")) {
            Path copy = tempDir.resolve(sourceName);
            Files.write(copy, markdownBytes);

            Document document = parser.parse(copy);

            assertThat(document.metadata().getString("document_id")).isEqualTo(markdownId);
            assertThat(document.metadata().getString("document_title")).isEqualTo(sourceName);
            assertThat(document.metadata().getString("source_name")).isEqualTo(sourceName);
            assertThat(document.metadata().getString("media_type")).isEqualTo("text/markdown");
            assertThat(document.metadata().toString()).doesNotContain(tempDir.toString());
        }

        Path pdfFixture = FIXTURES.resolve("synthetic-sample.pdf");
        byte[] pdfBytes = Files.readAllBytes(pdfFixture);
        Path pdfCopy = tempDir.resolve("renamed.PDF");
        Files.write(pdfCopy, pdfBytes);

        Document pdfDocument = parser.parse(pdfCopy);

        assertThat(pdfDocument.metadata().getString("document_id"))
                .isEqualTo(DocumentIdentity.documentId(pdfBytes));
        assertThat(pdfDocument.metadata().getString("media_type")).isEqualTo("application/pdf");
    }

    @Test
    void supportsShouldRecognizeOnlyExplicitExtensions() {
        assertThat(parser.supports(Path.of("sample.pdf"))).isTrue();
        assertThat(parser.supports(Path.of("sample.PDF"))).isTrue();
        assertThat(parser.supports(Path.of("sample.md"))).isTrue();
        assertThat(parser.supports(Path.of("sample.Markdown"))).isTrue();
        assertThat(parser.supports(Path.of("sample.txt"))).isFalse();
        assertThat(parser.supports(Path.of("sample"))).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    void invalidUtf8ShouldFailWithoutReplacingBytes() throws IOException {
        Path source = tempDir.resolve("invalid.md");
        Files.write(source, new byte[]{(byte) 0xC3, (byte) 0x28});

        Throwable thrown = catchThrowable(() -> parser.parse(source));

        assertThat(thrown)
                .isInstanceOf(SourceDocumentParser.SourceDocumentParseException.class)
                .hasMessage("Markdown document 'invalid.md' is not valid UTF-8.");
        assertThat(thrown.getMessage()).doesNotContain(tempDir.toString());
    }

    @Test
    void blankMarkdownShouldFailClearly() throws IOException {
        Path source = tempDir.resolve("blank.md");
        Files.writeString(source, " \n\t", UTF_8);

        Throwable thrown = catchThrowable(() -> parser.parse(source));

        assertThat(thrown)
                .isInstanceOf(SourceDocumentParser.SourceDocumentParseException.class)
                .hasMessage("Document 'blank.md' contains no text.");
    }

    @Test
    void malformedPdfShouldFailClearly() throws IOException {
        Path source = tempDir.resolve("broken.pdf");
        Files.writeString(source, "%PDF-1.4\nbroken\n%%EOF\n", UTF_8);

        Throwable thrown = catchThrowable(() -> parser.parse(source));

        assertThat(thrown).isInstanceOf(SourceDocumentParser.SourceDocumentParseException.class);
        assertThat(thrown.getMessage()).doesNotContain(tempDir.toString(), "broken\n");
    }

    @Test
    void parserFailureShouldExposeOnlySafeTopLevelMessage() throws IOException {
        Path source = tempDir.resolve("private.pdf");
        Files.write(source, new byte[]{1, 2, 3});
        SourceDocumentParser failingParser = new SourceDocumentParser(input -> {
            throw new IllegalStateException("sensitive parser detail");
        }, fixedClock);

        Throwable thrown = catchThrowable(() -> failingParser.parse(source));

        assertThat(thrown)
                .isInstanceOf(SourceDocumentParser.SourceDocumentParseException.class)
                .hasMessage("Failed to parse PDF document 'private.pdf'.")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
                .doesNotContain(tempDir.toString())
                .doesNotContain("sensitive parser detail");
    }

    @Test
    void pdfWithNoExtractedTextShouldFailClearly() throws IOException {
        Path source = tempDir.resolve("empty.pdf");
        Files.write(source, new byte[]{1, 2, 3});
        SourceDocumentParser emptyTextParser = new SourceDocumentParser(input -> new Document() {
            @Override
            public String text() {
                return " \n";
            }

            @Override
            public Metadata metadata() {
                return new Metadata();
            }
        }, fixedClock);

        Throwable thrown = catchThrowable(() -> emptyTextParser.parse(source));

        assertThat(thrown)
                .isInstanceOf(SourceDocumentParser.SourceDocumentParseException.class)
                .hasMessage("Document 'empty.pdf' contains no text.");
    }

    @Test
    void unsupportedExtensionShouldFailBeforeParsing() throws IOException {
        Path source = tempDir.resolve("notes.txt");
        Files.writeString(source, "Synthetic unsupported content", UTF_8);

        Throwable thrown = catchThrowable(() -> parser.parse(source));

        assertThat(thrown)
                .isInstanceOf(SourceDocumentParser.SourceDocumentParseException.class)
                .hasMessage("Unsupported document type for 'notes.txt'.");
        assertThat(thrown.getMessage()).doesNotContain("Synthetic unsupported content");
    }

    private static void assertBaseMetadata(
            Metadata metadata, String sourceName, String mediaType, byte[] rawBytes) {
        String sourceHash = independentSha256(rawBytes);
        assertThat(metadata.toMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "document_id", "doc_" + sourceHash,
                "document_title", sourceName,
                "source_name", sourceName,
                "source_sha256", sourceHash,
                "media_type", mediaType,
                "chunker_version", DocumentIdentity.CHUNKER_VERSION,
                "ingested_at", INGESTED_AT.toString()));
    }

    private static String independentSha256(byte[] rawBytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
