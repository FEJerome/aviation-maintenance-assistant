package cn.pandazi.aviation_maintenance_assistant.rag;

import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class RetrievalService {

    public interface TracedQueryTransformer extends QueryTransformer {
        Collection<Query> transform(Query query, String traceId);

        @Override
        default Collection<Query> transform(Query query) {
            return transform(query, "unknown");
        }
    }

    private final ContentRetriever contentRetriever;
    private final TracedQueryTransformer queryTransformer;

    public RetrievalService(ContentRetriever contentRetriever, TracedQueryTransformer queryTransformer) {
        this.contentRetriever = contentRetriever;
        this.queryTransformer = queryTransformer;
    }

    public List<RetrievedExcerpt> retrieve(String queryText, String traceId) {
        Collection<Query> queries;
        try {
            queries = queryTransformer.transform(Query.from(queryText), traceId);
        } catch (LlmCallExecutor.LlmCallException exception) {
            if (exception.code() == LlmCallExecutor.ErrorCode.QUOTA_EXHAUSTED
                    || exception.code() == LlmCallExecutor.ErrorCode.QUOTA_STATE_UNAVAILABLE) {
                throw exception;
            }
            throw new RetrievalException("QUERY_TRANSFORM_FAILED", traceId);
        } catch (RuntimeException exception) {
            throw new RetrievalException("QUERY_TRANSFORM_FAILED", traceId);
        }

        List<RetrievedExcerpt> excerpts = new ArrayList<>();
        try {
            for (Query query : queries) {
                List<Content> contents = contentRetriever.retrieve(query);
                if (contents == null) {
                    throw new RetrievalException("RETRIEVAL_UNAVAILABLE", traceId);
                }
                for (Content content : contents) {
                    excerpts.add(toExcerpt(content, traceId));
                }
            }
        } catch (RetrievalException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetrievalException("RETRIEVAL_UNAVAILABLE", traceId);
        }
        return List.copyOf(excerpts);
    }

    private RetrievedExcerpt toExcerpt(Content content, String traceId) {
        if (content == null || content.textSegment() == null) {
            throw new RetrievalException("RETRIEVAL_METADATA_INVALID", traceId);
        }

        Map<String, Object> metadata = content.textSegment().metadata().toMap();
        String excerptId = textValue(metadata.get("excerpt_id"));
        if (excerptId == null) {
            Object embeddingId = content.metadata().get(ContentMetadata.EMBEDDING_ID);
            excerptId = embeddingId == null ? null : embeddingId.toString();
        }
        String documentId = textValue(metadata.get("document_id"));
        String documentTitle = textValue(metadata.get("document_title"));
        Integer chunkIndex = integerValue(metadata.get("chunk_index"));
        Object scoreValue = content.metadata().get(ContentMetadata.SCORE);
        Double score = scoreValue instanceof Number number ? number.doubleValue() : null;

        if (isBlank(excerptId) || isBlank(documentId) || isBlank(documentTitle)
                || chunkIndex == null || chunkIndex < 0 || score == null) {
            throw new RetrievalException("RETRIEVAL_METADATA_INVALID", traceId);
        }

        return new RetrievedExcerpt(
                content.textSegment().text(),
                excerptId,
                documentId,
                documentTitle,
                chunkIndex,
                score);
    }

    private String textValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.valueOf(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static class RetrievalException extends RuntimeException {
        private final String code;
        private final String traceId;

        private RetrievalException(String code, String traceId) {
            super(safeMessage(code));
            this.code = code;
            this.traceId = traceId;
        }

        public String code() {
            return code;
        }

        public String traceId() {
            return traceId;
        }

        private static String safeMessage(String code) {
            return switch (code) {
                case "QUERY_TRANSFORM_FAILED" -> "Query transformation failed.";
                case "RETRIEVAL_METADATA_INVALID" -> "Retrieved excerpt metadata is invalid.";
                default -> "The retrieval service is unavailable.";
            };
        }
    }
}
