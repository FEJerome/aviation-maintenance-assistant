package cn.pandazi.aviation_maintenance_assistant.document.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

@Service
public class DocumentIngestionService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public DocumentIngestionService(@Lazy EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
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
                .embeddingStore(embeddingStore)
                .build();

        // 4. 执行嵌入与存储
        ingestor.ingest(document);
    }

    /**
     * 摄入文档，捕获异常不中断流程
     */
    public boolean ingestDocumentSafe(Path filePath) {
        try {
            ingestDocument(filePath);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to ingest: " + filePath.getFileName() + " - " + e.getMessage());
            return false;
        }
    }
}
