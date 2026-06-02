package cn.pandazi.aviation_maintenance_assistant.document.api;

import cn.pandazi.aviation_maintenance_assistant.document.service.DocumentIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/admin/ingest")
public class DocumentIngestionController {

    private final DocumentIngestionService ingestionService;

    public DocumentIngestionController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public String ingest() {
        Path pdfPath = Path.of("data/AC_65-9A.pdf");
        ingestionService.ingestDocument(pdfPath);
        return "PDF ingestion completed: " + pdfPath.getFileName();
    }
}
