package cn.pandazi.aviation_maintenance_assistant.document.api;

import cn.pandazi.aviation_maintenance_assistant.document.service.DocumentIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/admin/ingest")
public class DocumentIngestionController {

    private final DocumentIngestionService ingestionService;

    public DocumentIngestionController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public String ingest() throws IOException {
        Path dataDir = Paths.get("data");

        try (Stream<Path> paths = Files.list(dataDir)) {
            var pdfFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .collect(Collectors.toList());

            var successFiles = new java.util.ArrayList<String>();
            var failedFiles = new java.util.ArrayList<String>();

            for (Path pdfPath : pdfFiles) {
                boolean success = ingestionService.ingestDocumentSafe(pdfPath);
                if (success) {
                    successFiles.add(pdfPath.getFileName().toString());
                } else {
                    failedFiles.add(pdfPath.getFileName().toString());
                }
            }

            StringBuilder result = new StringBuilder();
            result.append("PDF ingestion completed.\n");
            result.append("Success: ").append(String.join(", ", successFiles)).append("\n");
            if (!failedFiles.isEmpty()) {
                result.append("Failed: ").append(String.join(", ", failedFiles));
            }
            return result.toString();
        }
    }
}
