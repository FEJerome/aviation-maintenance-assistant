package cn.pandazi.aviation_maintenance_assistant.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

@Configuration
public class RagConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Lazy
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(AmaProperties properties) {
        return ChromaEmbeddingStore.builder()
                .baseUrl(properties.getChroma().getBaseUrl())
                .collectionName(properties.getChroma().getCollection())
                .timeout(Duration.ofSeconds(15))
                .build();
    }

    @Lazy
    @Bean
    public ContentRetriever contentRetriever(
            @Lazy EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            AmaProperties properties) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(properties.getRag().getMaxResults())
                .minScore(properties.getRag().getMinScore())
                .build();
    }
}
