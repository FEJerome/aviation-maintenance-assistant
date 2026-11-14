package cn.pandazi.aviation_maintenance_assistant.chat.api;

import cn.pandazi.aviation_maintenance_assistant.chat.dto.ChatResponse;
import cn.pandazi.aviation_maintenance_assistant.chat.service.MaintenanceChatService;
import cn.pandazi.aviation_maintenance_assistant.config.RateLimitProperties;
import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MaintenanceChatService chatService;

    @MockBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void returnsTheAdditiveResponseContract() throws Exception {
        when(chatService.process(any(), any())).thenReturn(new ChatResponse(
                "Synthetic answer",
                "conversation",
                ChatResponse.Outcome.ANSWERED,
                "trace-chat",
                ChatResponse.GenerationMode.NON_STREAMING,
                false,
                null,
                List.of()));

        mvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"message\":\"What is AMM?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Synthetic answer"))
                .andExpect(jsonPath("$.outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.traceId").value("trace-chat"))
                .andExpect(jsonPath("$.generationMode").value("NON_STREAMING"))
                .andExpect(jsonPath("$.citations").isArray());
    }

    @Test
    void invalidRequestDoesNotReachTheService() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        verifyNoInteractions(chatService);
    }

    @Test
    void retiredPathReturns404() throws Exception {
        mvc.perform(post("/chat")
                        .contentType("application/json")
                        .content("{\"message\":\"test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verifyNoInteractions(chatService);
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        mvc.perform(get("/api/chat"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        verifyNoInteractions(chatService);
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType("text/plain")
                        .content("message=test"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        verifyNoInteractions(chatService);
    }

    @Test
    void quotaFailureUsesSafeHttp429Error() throws Exception {
        LlmCallExecutor.LlmCallException exception = mock(LlmCallExecutor.LlmCallException.class);
        when(exception.code()).thenReturn(LlmCallExecutor.ErrorCode.QUOTA_EXHAUSTED);
        when(exception.getMessage()).thenReturn("Daily LLM quota is exhausted.");
        when(exception.traceId()).thenReturn("trace-quota");
        when(chatService.process(any(), any())).thenThrow(exception);

        mvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"message\":\"What is AMM?\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXHAUSTED"))
                .andExpect(jsonPath("$.traceId").value("trace-quota"));
    }
}
