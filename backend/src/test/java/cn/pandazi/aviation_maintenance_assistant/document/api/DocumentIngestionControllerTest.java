package cn.pandazi.aviation_maintenance_assistant.document.api;

import cn.pandazi.aviation_maintenance_assistant.chat.api.ApiExceptionHandler;
import cn.pandazi.aviation_maintenance_assistant.document.dto.IngestionReport;
import cn.pandazi.aviation_maintenance_assistant.document.service.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentIngestionControllerTest {

    @Test
    void returnsTheStructuredBatchReport() throws Exception {
        DocumentIngestionService service = mock(DocumentIngestionService.class);
        when(service.ingestDirectory()).thenReturn(new IngestionReport(
                "COMPLETED_WITH_ERRORS",
                "synthetic-documents",
                List.of(new IngestionReport.DocumentResult(
                        "sample.md", "FAILED", null, null, null, "DOCUMENT_PARSE_FAILED")),
                List.of("notes.txt")));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new DocumentIngestionController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(post("/api/admin/ingest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.collection").value("synthetic-documents"))
                .andExpect(jsonPath("$.documents[0].errorCode").value("DOCUMENT_PARSE_FAILED"))
                .andExpect(jsonPath("$.ignored[0]").value("notes.txt"));
    }
}
