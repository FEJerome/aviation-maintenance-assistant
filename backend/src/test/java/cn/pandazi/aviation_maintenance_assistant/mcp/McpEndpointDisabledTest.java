package cn.pandazi.aviation_maintenance_assistant.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With the default (disabled) profile the MCP transport must not be registered at all:
 * the endpoint answers 404 with the standard safe error contract.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpEndpointDisabledTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Environment environment;

    @Test
    void defaultContextResolvesTheServerSwitchToDisabled() {
        // Placeholder resolution without AMA_MCP_ENABLED: the effective security switch
        // and the user-facing entry both default to false.
        assertThat(environment.getProperty("spring.ai.mcp.server.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("ama.mcp.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("ama.mcp.path")).isEqualTo("/mcp");
    }

    @Test
    void mcpEndpointIsNotFoundWhenDisabled() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, "application/json, text/event-stream");

        ResponseEntity<String> response = restTemplate.exchange(
                "/mcp",
                HttpMethod.POST,
                new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", headers),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("NOT_FOUND");
        assertThat(response.getBody()).contains("traceId");
    }
}
