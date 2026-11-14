package cn.pandazi.aviation_maintenance_assistant.document.api;

import cn.pandazi.aviation_maintenance_assistant.document.dto.IngestionReport;
import cn.pandazi.aviation_maintenance_assistant.document.service.DocumentIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ingest")
public class DocumentIngestionController {

    private final DocumentIngestionService ingestionService;

    public DocumentIngestionController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public IngestionReport ingest() {
        return ingestionService.ingestDirectory();
    }
}
