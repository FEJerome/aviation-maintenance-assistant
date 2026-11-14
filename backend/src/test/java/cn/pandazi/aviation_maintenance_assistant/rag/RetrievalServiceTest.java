package cn.pandazi.aviation_maintenance_assistant.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    private final ContentRetriever retriever = mock(ContentRetriever.class);
    private final RetrievalService.TracedQueryTransformer transformer =
            mock(RetrievalService.TracedQueryTransformer.class);
    private final RetrievalService service = new RetrievalService(retriever, transformer);

    @Test
    void returnsStableMetadataAndScoreFromTheTransformedQuery() {
        when(transformer.transform(any(Query.class), eq("trace-retrieval")))
                .thenReturn(List.of(Query.from("translated query")));
        when(retriever.retrieve(Query.from("translated query")))
                .thenReturn(List.of(content(true)));

        List<RetrievedExcerpt> result = service.retrieve("original query", "trace-retrieval");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).excerptId()).isEqualTo("exr_test");
        assertThat(result.get(0).documentId()).isEqualTo("doc_test");
        assertThat(result.get(0).chunkIndex()).isEqualTo(2);
        assertThat(result.get(0).score()).isEqualTo(0.81);
        assertThat(result.get(0).toCitation().documentTitle()).isEqualTo("synthetic.md");
        verify(retriever).retrieve(Query.from("translated query"));
    }

    @Test
    void missingStableMetadataFailsInsteadOfInventingAnId() {
        when(transformer.transform(any(Query.class), eq("trace-metadata")))
                .thenReturn(List.of(Query.from("query")));
        when(retriever.retrieve(any(Query.class))).thenReturn(List.of(content(false)));

        assertThatThrownBy(() -> service.retrieve("query", "trace-metadata"))
                .isInstanceOf(RetrievalService.RetrievalException.class)
                .extracting("code")
                .isEqualTo("RETRIEVAL_METADATA_INVALID");
    }

    @Test
    void transformerFailureDoesNotSilentlyUseTheOriginalQuery() {
        when(transformer.transform(any(Query.class), eq("trace-transform")))
                .thenThrow(new IllegalStateException("sensitive provider detail"));

        assertThatThrownBy(() -> service.retrieve("query", "trace-transform"))
                .isInstanceOf(RetrievalService.RetrievalException.class)
                .hasMessage("Query transformation failed.")
                .extracting("code")
                .isEqualTo("QUERY_TRANSFORM_FAILED");
    }

    private Content content(boolean includeMetadata) {
        Metadata metadata = new Metadata();
        if (includeMetadata) {
            metadata.put("excerpt_id", "exr_test");
            metadata.put("document_id", "doc_test");
            metadata.put("document_title", "synthetic.md");
            metadata.put("chunk_index", 2);
        }
        return Content.from(
                TextSegment.from("Synthetic excerpt", metadata),
                Map.of(ContentMetadata.SCORE, 0.81));
    }
}
