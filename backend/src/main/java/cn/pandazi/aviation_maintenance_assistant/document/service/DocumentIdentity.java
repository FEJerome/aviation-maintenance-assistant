package cn.pandazi.aviation_maintenance_assistant.document.service;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Deterministic identities and metadata for source documents and their excerpts.
 */
public final class DocumentIdentity {

    public static final String CHUNKER_VERSION = "recursive-500-50-v1";

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;
    private static final String DOCUMENT_ID = "document_id";
    private static final String EXCERPT_ID = "excerpt_id";

    private DocumentIdentity() {
    }

    public static DocumentSplitter newChunker() {
        return DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
    }

    public static String sourceSha256(byte[] rawBytes) {
        Objects.requireNonNull(rawBytes, "rawBytes");
        return sha256(rawBytes);
    }

    public static String documentId(byte[] rawBytes) {
        return "doc_" + sourceSha256(rawBytes);
    }

    public static String excerptId(String documentId, int chunkIndex, String exactChunkText) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(exactChunkText, "exactChunkText");
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be non-negative");
        }

        String identityText = documentId + "\n" + CHUNKER_VERSION + "\n"
                + chunkIndex + "\n" + exactChunkText;
        return "exr_" + sha256(identityText.getBytes(StandardCharsets.UTF_8));
    }

    public static Metadata baseMetadata(
            String sourceName, String mediaType, byte[] rawBytes, Instant ingestedAt) {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(rawBytes, "rawBytes");
        Objects.requireNonNull(ingestedAt, "ingestedAt");

        String sourceHash = sourceSha256(rawBytes);
        Metadata metadata = new Metadata();
        metadata.put(DOCUMENT_ID, "doc_" + sourceHash);
        metadata.put("document_title", sourceName);
        metadata.put("source_name", sourceName);
        metadata.put("source_sha256", sourceHash);
        metadata.put("media_type", mediaType);
        metadata.put("chunker_version", CHUNKER_VERSION);
        metadata.put("ingested_at", ingestedAt.toString());
        return metadata;
    }

    public static Metadata excerptMetadata(Metadata baseMetadata, int chunkIndex, String exactChunkText) {
        Objects.requireNonNull(baseMetadata, "baseMetadata");
        String documentId = baseMetadata.getString(DOCUMENT_ID);
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("baseMetadata must contain document_id");
        }

        Metadata metadata = baseMetadata.copy();
        metadata.put(EXCERPT_ID, excerptId(documentId, chunkIndex, exactChunkText));
        metadata.put("chunk_index", chunkIndex);
        return metadata;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
