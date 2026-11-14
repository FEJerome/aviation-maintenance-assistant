package cn.pandazi.aviation_maintenance_assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String clientIp = resolveClientIp(request);

        if (path.startsWith("/api/admin/ingest") && !isInternalIp(clientIp)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "ADMIN_FORBIDDEN",
                    "The ingestion endpoint is restricted to local networks.");
            return;
        }

        if (properties.isEnabled() && path.startsWith("/api/chat")) {
            Bucket bucket = buckets.computeIfAbsent(clientIp, key -> Bucket.builder()
                    .addLimit(Bandwidth.classic(
                            properties.getCapacity(),
                            Refill.intervally(properties.getCapacity(), parsePeriod())))
                    .build());
            if (!bucket.tryConsume(1)) {
                writeError(response, 429, "RATE_LIMITED",
                        "Too many chat requests.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Duration parsePeriod() {
        return Duration.parse("PT" + properties.getRefillPeriod().toUpperCase());
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustProxyHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isInternalIp(String value) {
        try {
            InetAddress address = parseLiteralAddress(value);
            if (address == null || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                return false;
            }
            if (address.isLoopbackAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            if (bytes.length == 4) {
                return isPrivateIpv4(bytes, 0);
            }
            return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        } catch (Exception exception) {
            return false;
        }
    }

    private InetAddress parseLiteralAddress(String value) throws Exception {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.indexOf(':') >= 0) {
            if (!value.matches("[0-9A-Fa-f:.]+")) {
                return null;
            }
            return InetAddress.getByName(value);
        }

        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            if (!parts[index].matches("\\d{1,3}")) {
                return null;
            }
            int octet = Integer.parseInt(parts[index]);
            if (octet > 255) {
                return null;
            }
            bytes[index] = (byte) octet;
        }
        return InetAddress.getByAddress(bytes);
    }

    private boolean isPrivateIpv4(byte[] bytes, int offset) {
        int first = bytes[offset] & 0xff;
        int second = bytes[offset + 1] & 0xff;
        return first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
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
}
