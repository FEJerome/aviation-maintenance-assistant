package cn.pandazi.aviation_maintenance_assistant.mcp;

import cn.pandazi.aviation_maintenance_assistant.rag.RetrievedExcerpt;
import cn.pandazi.aviation_maintenance_assistant.rag.RetrievalService;
import cn.pandazi.aviation_maintenance_assistant.support.FakeOpenAiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Loopback-only integration of the enabled MCP endpoint against the fake OpenAI-compatible
 * server and a stubbed retriever. One daily quota models the QUOTA_EXHAUSTED failure path.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "AMA_MCP_ENABLED=true",
                "ama.mcp.auth-token=test-mcp-token-0123456789",
                "ama.llm.quota-enabled=true",
                "ama.llm.daily-quota=1",
                "ama.state-path=./build/test-state-mcp-integration",
                "rate-limit.chat.enabled=false"
        })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpServerIntegrationTest {

    private static final String AUTH = "Bearer test-mcp-token-0123456789";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static FakeOpenAiServer fake;

    @DynamicPropertySource
    static void registerFakeLlm(DynamicPropertyRegistry registry) {
        registry.add("ama.llm.base-url", () -> fake.baseUrl());
        registry.add("ama.llm.api-key", () -> "test-only-credential");
        registry.add("ama.llm.model", () -> "synthetic-chat");
    }

    @BeforeAll
    static void startFakeAndResetQuota() throws IOException {
        fake = new FakeOpenAiServer();
        Files.deleteIfExists(Path.of("build/test-state-mcp-integration/daily-llm-quota.json"));
    }

    @AfterAll
    static void stopFake() {
        if (fake != null) {
            fake.close();
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private RetrievalService retrievalService;

    @Test
    @Order(1)
    void initializeToolsListAndAnsweredCallReturnOriginalExcerptText() throws Exception {
        String session = initialize();
        JsonNode tools = rpc(session, 2, "tools/list", null);
        assertThat(tools.path("tools").size()).isEqualTo(1);
        assertThat(tools.path("tools").get(0).path("name").asText())
                .isEqualTo("ask_maintenance_question");

        RetrievedExcerpt excerpt = new RetrievedExcerpt(
                "Original manual excerpt: CTLS oil pressure limits 2.8-5.5 bar.",
                "exr_mcp_1",
                "doc_mcp",
                "ctls-manual.md",
                3,
                0.88);
        when(retrievalService.retrieve(any(), any())).thenReturn(List.of(excerpt));
        int callsBefore = fake.calls();

        JsonNode result = rpc(session, 3, "tools/call", callParams(
                "CTLS Rotax 912 engine oil pressure", null));

        assertThat(result.path("isError").asBoolean(false)).isFalse();
        JsonNode answer = MAPPER.readTree(result.path("content").get(0).path("text").asText());
        assertThat(answer.path("outcome").asText()).isEqualTo("ANSWERED");
        assertThat(answer.path("reply").asText()).isEqualTo("Synthetic answer");
        assertThat(answer.path("traceId").asText()).isNotBlank();
        assertThat(answer.path("conversationId").asText()).isNotBlank();
        assertThat(answer.path("generationMode").asText()).isEqualTo("NON_STREAMING");
        JsonNode citation = answer.path("citations").get(0);
        assertThat(citation.path("excerptId").asText()).isEqualTo("exr_mcp_1");
        assertThat(citation.path("documentTitle").asText()).isEqualTo("ctls-manual.md");
        assertThat(citation.path("text").asText())
                .isEqualTo("Original manual excerpt: CTLS oil pressure limits 2.8-5.5 bar.");
        assertThat(fake.calls()).isEqualTo(callsBefore + 1);
    }

    @Test
    @Order(2)
    void exhaustedQuotaSurfacesSafeIsErrorWithoutCallingTheModel() throws Exception {
        String session = initialize();
        when(retrievalService.retrieve(any(), any())).thenReturn(List.of(
                new RetrievedExcerpt("evidence", "exr_q", "doc_q", "manual.md", 0, 0.9)));
        int callsBefore = fake.calls();

        JsonNode result = rpc(session, 4, "tools/call", callParams(
                "CTLS Rotax 912 engine oil pressure", null));

        assertThat(result.path("isError").asBoolean(false)).isTrue();
        String errorText = result.path("content").get(0).path("text").asText();
        assertThat(errorText).contains("QUOTA_EXHAUSTED");
        assertThat(errorText).contains("traceId=");
        assertThat(errorText).doesNotContain("synthetic provider detail");
        assertThat(fake.calls()).isEqualTo(callsBefore);
    }

    @Test
    @Order(3)
    void noHitRetrievalReturnsNoAnswerOutcomeWithoutModelCall() throws Exception {
        String session = initialize();
        when(retrievalService.retrieve(any(), any())).thenReturn(List.of());
        int callsBefore = fake.calls();

        JsonNode result = rpc(session, 5, "tools/call", callParams(
                "CTLS Rotax 912 engine oil pressure", null));

        assertThat(result.path("isError").asBoolean(false)).isFalse();
        JsonNode answer = MAPPER.readTree(result.path("content").get(0).path("text").asText());
        assertThat(answer.path("outcome").asText()).isEqualTo("NO_ANSWER");
        assertThat(answer.path("citations").size()).isZero();
        assertThat(fake.calls()).isEqualTo(callsBefore);
    }

    @Test
    @Order(4)
    void clarificationShortcutDoesNotInvokeTheModel() throws Exception {
        String session = initialize();
        int callsBefore = fake.calls();

        JsonNode result = rpc(session, 6, "tools/call", callParams(
                "CTLS engine oil pressure", null));

        assertThat(result.path("isError").asBoolean(false)).isFalse();
        JsonNode answer = MAPPER.readTree(result.path("content").get(0).path("text").asText());
        assertThat(answer.path("outcome").asText()).isEqualTo("CLARIFICATION_REQUIRED");
        assertThat(fake.calls()).isEqualTo(callsBefore);
    }

    @Test
    @Order(5)
    void mismatchedAircraftEngineIsBlockedWithoutModelCall() throws Exception {
        String session = initialize();
        int callsBefore = fake.calls();

        JsonNode result = rpc(session, 7, "tools/call", callParams(
                "B737-800 PW1100G engine oil pressure", null));

        assertThat(result.path("isError").asBoolean(false)).isFalse();
        JsonNode answer = MAPPER.readTree(result.path("content").get(0).path("text").asText());
        assertThat(answer.path("outcome").asText()).isEqualTo("VALIDATION_BLOCKED");
        assertThat(fake.calls()).isEqualTo(callsBefore);
    }

    @Test
    @Order(6)
    void missingTokenIsRejectedBeforeToolDispatch() throws Exception {
        String url = url();
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 100);
        body.put("method", "tools/list");
        body.set("params", MAPPER.createObjectNode());
        HttpHeaders headers = mcpHeaders(null, null);
        headers.remove(HttpHeaders.AUTHORIZATION);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body.toString(), headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).contains("UNAUTHORIZED");
        assertThat(response.getBody()).contains("traceId");
    }

    private String initialize() throws Exception {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("protocolVersion", "2025-03-26");
        params.set("capabilities", MAPPER.createObjectNode());
        ObjectNode clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", "ama-loopback-test");
        clientInfo.put("version", "1.0");
        params.set("clientInfo", clientInfo);

        HttpHeaders headers = mcpHeaders(null, null);
        ResponseEntity<String> response = exchange(1, "initialize", params, headers);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode result = extractResult(response.getBody(), 1);
        assertThat(result.path("serverInfo").path("name").asText())
                .isEqualTo("aviation-maintenance-assistant");
        String protocolVersion = result.path("protocolVersion").asText();

        String sessionId = response.getHeaders().getFirst("Mcp-Session-Id");
        notify(sessionId, "notifications/initialized", protocolVersion);
        return sessionId;
    }

    private JsonNode rpc(String sessionId, int id, String method, ObjectNode params) throws Exception {
        HttpHeaders headers = mcpHeaders(sessionId, "2025-03-26");
        ResponseEntity<String> response = exchange(id, method, params, headers);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return extractResult(response.getBody(), id);
    }

    private void notify(String sessionId, String method, String protocolVersion) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        restTemplate.exchange(url(), HttpMethod.POST,
                new HttpEntity<>(body.toString(), mcpHeaders(sessionId, protocolVersion)),
                String.class);
    }

    private ResponseEntity<String> exchange(
            int id, String method, ObjectNode params, HttpHeaders headers) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        if (params != null) {
            body.set("params", params);
        }
        return restTemplate.exchange(
                url(), HttpMethod.POST, new HttpEntity<>(body.toString(), headers), String.class);
    }

    private JsonNode extractResult(String body, int id) throws Exception {
        List<String> payloads = new ArrayList<>();
        for (String line : body.split("\n")) {
            if (line.startsWith("data:")) {
                payloads.add(line.substring("data:".length()).trim());
            }
        }
        if (payloads.isEmpty()) {
            payloads.add(body.trim());
        }
        for (String payload : payloads) {
            JsonNode message = MAPPER.readTree(payload);
            if (message.has("id") && message.path("id").asInt() == id) {
                assertThat(message.has("error"))
                        .as("JSON-RPC error: %s", message.path("error")).isFalse();
                return message.path("result");
            }
        }
        throw new AssertionError("No JSON-RPC response with id " + id + " in: " + body);
    }

    private ObjectNode callParams(String question, String conversationId) {
        ObjectNode arguments = MAPPER.createObjectNode();
        arguments.put("question", question);
        if (conversationId != null) {
            arguments.put("conversationId", conversationId);
        }
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", "ask_maintenance_question");
        params.set("arguments", arguments);
        return params;
    }

    private HttpHeaders mcpHeaders(String sessionId, String protocolVersion) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, "application/json, text/event-stream");
        headers.set(HttpHeaders.AUTHORIZATION, AUTH);
        if (sessionId != null) {
            headers.set("Mcp-Session-Id", sessionId);
        }
        if (protocolVersion != null) {
            headers.set("MCP-Protocol-Version", protocolVersion);
        }
        return headers;
    }

    private String url() {
        return "/mcp";
    }
}
