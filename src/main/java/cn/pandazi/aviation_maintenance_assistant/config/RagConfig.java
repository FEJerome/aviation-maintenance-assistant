package cn.pandazi.aviation_maintenance_assistant.config;

import cn.pandazi.aviation_maintenance_assistant.rag.TranslationQueryTransformer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
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
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${langchain4j.chroma.embedding-store.base-url}") String baseUrl,
            @Value("${langchain4j.chroma.embedding-store.collection-name}") String collectionName) {
        return ChromaEmbeddingStore.builder()
                .baseUrl(baseUrl)
                .collectionName(collectionName)
                .timeout(Duration.ofSeconds(15))
                .build();
    }

    @Lazy
    @Bean
    public ContentRetriever contentRetriever(
            @Lazy EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            @Value("${app.rag.retrieval.max-results:5}") int maxResults,
            @Value("${app.rag.retrieval.min-score:0.6}") double minScore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

    @Lazy
    @Bean
    public RetrievalAugmentor retrievalAugmentor(
            ChatModel chatModel,
            @Lazy ContentRetriever contentRetriever,
            @Value("${app.rag.query-translation.enabled:true}") boolean translationEnabled) {
        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(new TranslationQueryTransformer(chatModel, translationEnabled))
                .contentRetriever(contentRetriever)
                .build();
    }
}
