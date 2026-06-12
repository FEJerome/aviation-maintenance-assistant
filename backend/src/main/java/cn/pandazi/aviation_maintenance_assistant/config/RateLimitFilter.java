package cn.pandazi.aviation_maintenance_assistant.config;

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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 级限流与内网访问控制 Filter
 * <p>
 * 1. 对 /api/chat 接口按客户端 IP 进行令牌桶限流。
 * 2. 对 /api/admin/ingest 接口限制仅内网 IP 可访问。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 文档摄入接口仅限内网
        if (path.startsWith("/api/admin/ingest")) {
            String clientIp = resolveClientIp(request);
            if (!isInternalIp(clientIp)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"该接口仅限内网访问\"}");
                return;
            }
        }

        // 聊天接口按 IP 限流
        if (path.startsWith("/api/chat")) {
            String clientIp = resolveClientIp(request);
            Bucket bucket = resolveBucket(clientIp);
            if (!bucket.tryConsume(1)) {
                response.setStatus(429); // HttpServletResponse.SC_TOO_MANY_REQUESTS
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"请求过于频繁，请 1 小时后再试\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket resolveBucket(String ip) {
        return buckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(
                        properties.getCapacity(),
                        Refill.intervally(properties.getCapacity(), parsePeriod(properties.getRefillPeriod()))
                ))
                .build());
    }

    private Duration parsePeriod(String period) {
        return Duration.parse("PT" + period.toUpperCase());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            // X-Forwarded-For 可能为逗号分隔的多个 IP，取第一个（最外层客户端）
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private boolean isInternalIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return ip.equals("127.0.0.1") || ip.startsWith("127.")
                || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")
                || ip.startsWith("10.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.")
                || ip.startsWith("172.18.") || ip.startsWith("172.19.")
                || ip.startsWith("172.20.") || ip.startsWith("172.21.")
                || ip.startsWith("172.22.") || ip.startsWith("172.23.")
                || ip.startsWith("172.24.") || ip.startsWith("172.25.")
                || ip.startsWith("172.26.") || ip.startsWith("172.27.")
                || ip.startsWith("172.28.") || ip.startsWith("172.29.")
                || ip.startsWith("172.30.") || ip.startsWith("172.31.")
                || ip.startsWith("192.168.");
    }
}
