package cn.pandazi.aviation_maintenance_assistant.document.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

@Service
public class DocumentIngestionService {

    private final String chromaBaseUrl;
    private final String collectionName;
    private EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public DocumentIngestionService(
            @Value("${langchain4j.chroma.embedding-store.base-url}") String chromaBaseUrl,
            @Value("${langchain4j.chroma.embedding-store.collection-name}") String collectionName) {

        this.chromaBaseUrl = chromaBaseUrl;
        this.collectionName = collectionName;
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    }

    private synchronized EmbeddingStore<TextSegment> getEmbeddingStore() {
        if (this.embeddingStore == null) {
            this.embeddingStore = ChromaEmbeddingStore.builder()
                    .baseUrl(chromaBaseUrl)
                    .collectionName(collectionName)
                    .timeout(Duration.ofSeconds(15))
                    .build();
        }
        return this.embeddingStore;
    }

    public void ingestDocument(Path filePath) {
        // 1. 加载并解析 PDF
        Document document = loadDocument(filePath, new ApacheTikaDocumentParser());

        // 2. 分块：每块 500 字符，重叠 50 字符
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);

        // 3. 构建 ingestor 管线
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(getEmbeddingStore())
                .build();

        // 4. 执行嵌入与存储
        ingestor.ingest(document);
    }
}
