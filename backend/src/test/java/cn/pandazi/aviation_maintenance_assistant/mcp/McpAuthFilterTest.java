package cn.pandazi.aviation_maintenance_assistant.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class McpAuthFilterTest {

    private static final String TOKEN = "test-mcp-token-0123456789";

    private final McpProperties properties = enabledProperties();
    private final McpAuthFilter filter = new McpAuthFilter(properties, new ObjectMapper());

    @Test
    void loopbackCallerWithValidTokenProceeds() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("127.0.0.1", "Bearer " + TOKEN), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void ipv6LoopbackCallerWithValidTokenProceeds() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("::1", "Bearer " + TOKEN), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void missingTokenIsRejectedWithSafeJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("127.0.0.1", null), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
        assertThat(response.getContentAsString()).contains("traceId");
        assertThat(response.getContentAsString()).doesNotContain(TOKEN);
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("127.0.0.1", "Bearer wrong-token"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void nonBearerSchemeIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("127.0.0.1", TOKEN), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void nonLoopbackCallerIsRejectedEvenWithValidToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("192.168.1.10", "Bearer " + TOKEN), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
        assertThat(response.getContentAsString()).contains("traceId");
    }

    @Test
    void spoofedLoopbackForwardedHeaderCannotBypassRemoteAddressCheck() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = request("10.0.0.5", "Bearer " + TOKEN);
        request.addHeader("X-Forwarded-For", "127.0.0.1");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
    }

    @Test
    void spoofedPublicForwardedHeaderCannotBlockLoopbackCaller() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = request("127.0.0.1", "Bearer " + TOKEN);
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void ipv6LoopbackIgnoresForwardedHeaderEntirely() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = request("::1", "Bearer " + TOKEN);
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        request.addHeader("Forwarded", "for=203.0.113.10");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void malformedRemoteAddressFailsClosed() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("not-an-ip", "Bearer " + TOKEN), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void nullConfiguredTokenRejectsEveryCall() throws Exception {
        McpProperties noToken = new McpProperties();
        noToken.setEnabled(true);
        McpAuthFilter strict = new McpAuthFilter(noToken, new ObjectMapper());

        MockHttpServletResponse response = new MockHttpServletResponse();
        strict.doFilter(request("127.0.0.1", "Bearer anything"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private static McpProperties enabledProperties() {
        McpProperties properties = new McpProperties();
        properties.setEnabled(true);
        properties.setAuthToken(TOKEN);
        properties.setPath("/mcp");
        return properties;
    }

    private static MockHttpServletRequest request(String remoteAddr, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.setRemoteAddr(remoteAddr);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }
}
