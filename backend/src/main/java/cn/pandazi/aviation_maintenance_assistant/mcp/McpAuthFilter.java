package cn.pandazi.aviation_maintenance_assistant.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Loopback-and-token guard for the read-only MCP endpoint.
 *
 * Authorization decisions use only {@code request.getRemoteAddr()}. Forwarded headers
 * (X-Forwarded-For, Forwarded) are permanently ignored, so a spoofed header can never
 * turn a non-loopback caller into a trusted one.
 */
public class McpAuthFilter extends OncePerRequestFilter {

    private final McpProperties properties;
    private final ObjectMapper objectMapper;

    public McpAuthFilter(McpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isLoopback(request.getRemoteAddr())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "The MCP endpoint is restricted to loopback callers.");
            return;
        }

        if (!hasValidBearerToken(request.getHeader("Authorization"))) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                    "A valid bearer token is required.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLoopback(String remoteAddr) {
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean hasValidBearerToken(String authorization) {
        String token = properties.getAuthToken();
        if (token == null || authorization == null) {
            return false;
        }
        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix)) {
            return false;
        }
        String presented = authorization.substring(prefix.length());
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("traceId", UUID.randomUUID().toString());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "ama.mcp", name = "enabled", havingValue = "true")
    static class McpAuthFilterConfiguration {

        @Bean
        FilterRegistrationBean<McpAuthFilter> mcpAuthFilterRegistration(
                McpProperties properties, ObjectMapper objectMapper) {
            FilterRegistrationBean<McpAuthFilter> registration =
                    new FilterRegistrationBean<>(new McpAuthFilter(properties, objectMapper));
            registration.addUrlPatterns(properties.getPath());
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}
