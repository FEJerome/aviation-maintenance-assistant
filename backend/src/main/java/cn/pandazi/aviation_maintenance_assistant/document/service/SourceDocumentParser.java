package cn.pandazi.aviation_maintenance_assistant.document.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

/**
 * Parses supported source files through explicit PDF and Markdown branches.
 */
@Component
public class SourceDocumentParser {

    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String MARKDOWN_MEDIA_TYPE = "text/markdown";

    private final DocumentParser pdfParser;
    private final Clock clock;

    public SourceDocumentParser() {
        this(new ApacheTikaDocumentParser(), Clock.systemUTC());
    }

    SourceDocumentParser(DocumentParser pdfParser, Clock clock) {
        this.pdfParser = Objects.requireNonNull(pdfParser, "pdfParser");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean supports(Path source) {
        if (source == null || source.getFileName() == null) {
            return false;
        }
        return mediaTypeFor(source.getFileName().toString()) != null;
    }

    public Document parse(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (source.getFileName() == null) {
            throw new SourceDocumentParseException("Document path must include a file name.");
        }

        String sourceName = source.getFileName().toString();
        String mediaType = mediaTypeFor(sourceName);
        if (mediaType == null) {
            throw new SourceDocumentParseException(
                    "Unsupported document type for '" + sourceName + "'.");
        }

        byte[] rawBytes = Files.readAllBytes(source);
        String text = PDF_MEDIA_TYPE.equals(mediaType)
                ? parsePdf(rawBytes, sourceName)
                : parseMarkdown(rawBytes, sourceName);

        if (text == null || text.isBlank()) {
            throw new SourceDocumentParseException(
                    "Document '" + sourceName + "' contains no text.");
        }

        Metadata metadata = DocumentIdentity.baseMetadata(
                sourceName, mediaType, rawBytes, clock.instant());
        return Document.from(text, metadata);
    }

    private String parsePdf(byte[] rawBytes, String sourceName) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(rawBytes)) {
            Document document = pdfParser.parse(input);
            return document == null ? null : document.text();
        } catch (Exception e) {
            throw new SourceDocumentParseException(
                    "Failed to parse PDF document '" + sourceName + "'.", e);
        }
    }

    private String parseMarkdown(byte[] rawBytes, String sourceName) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawBytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new SourceDocumentParseException(
                    "Markdown document '" + sourceName + "' is not valid UTF-8.", e);
        }
    }

    private static String mediaTypeFor(String sourceName) {
        int extensionSeparator = sourceName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == sourceName.length() - 1) {
            return null;
        }

        String extension = sourceName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> PDF_MEDIA_TYPE;
            case "md", "markdown" -> MARKDOWN_MEDIA_TYPE;
            default -> null;
        };
    }

    public static final class SourceDocumentParseException extends RuntimeException {
        private SourceDocumentParseException(String message) {
            super(message);
        }

        private SourceDocumentParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
