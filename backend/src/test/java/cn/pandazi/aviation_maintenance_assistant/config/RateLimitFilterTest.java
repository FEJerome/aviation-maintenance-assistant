package cn.pandazi.aviation_maintenance_assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    @Test
    void spoofedLoopbackHeaderDoesNotBypassAdminRestriction() throws Exception {
        RateLimitFilter filter = filter(20, true, false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/admin/ingest", "203.0.113.10", "127.0.0.1"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ADMIN_FORBIDDEN");
    }

    @Test
    void spoofedPublicHeaderDoesNotTurnLoopbackIntoAnExternalClient() throws Exception {
        RateLimitFilter filter = filter(20, true, false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/admin/ingest", "127.0.0.1", "203.0.113.10"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void disabledRateLimitDoesNotConsumeBuckets() throws Exception {
        RateLimitFilter filter = filter(1, false, false);
        FilterChain chain = mock(FilterChain.class);

        for (int index = 0; index < 3; index++) {
            filter.doFilter(
                    request("/api/chat", "127.0.0.1", null),
                    new MockHttpServletResponse(),
                    chain);
        }

        verify(chain, times(3)).doFilter(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void spoofedHeadersDoNotMoveTheClientToAnotherBucket() throws Exception {
        RateLimitFilter filter = filter(1, true, false);
        filter.doFilter(request("/api/chat", "127.0.0.1", "10.0.0.1"),
                new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletResponse second = new MockHttpServletResponse();

        filter.doFilter(request("/api/chat", "127.0.0.1", "10.0.0.2"), second, new MockFilterChain());

        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void trustedProxyModeUsesTheFirstForwardedAddress() throws Exception {
        RateLimitFilter filter = filter(1, true, true);
        filter.doFilter(request("/api/chat", "127.0.0.1", "10.0.0.1, 192.168.1.1"),
                new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletResponse second = new MockHttpServletResponse();

        filter.doFilter(request("/api/chat", "127.0.0.1", "10.0.0.2"), second, new MockFilterChain());

        assertThat(second.getStatus()).isEqualTo(200);
    }

    private RateLimitFilter filter(long capacity, boolean enabled, boolean trustProxy) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCapacity(capacity);
        properties.setRefillPeriod("1h");
        properties.setEnabled(enabled);
        properties.setTrustProxyHeaders(trustProxy);
        return new RateLimitFilter(properties, new ObjectMapper());
    }

    private MockHttpServletRequest request(String path, String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
