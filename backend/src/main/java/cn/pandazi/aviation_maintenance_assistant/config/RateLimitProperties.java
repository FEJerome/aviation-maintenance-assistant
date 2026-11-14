package cn.pandazi.aviation_maintenance_assistant.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "rate-limit.chat")
public class RateLimitProperties {

    private long capacity = 20;
    private String refillPeriod = "1h";
    private boolean enabled = true;
    private boolean trustProxyHeaders = false;

    @PostConstruct
    public void validate() {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Rate-limit capacity must be positive");
        }
        Duration period = Duration.parse("PT" + refillPeriod.toUpperCase());
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("Rate-limit refill period must be positive");
        }
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public String getRefillPeriod() {
        return refillPeriod;
    }

    public void setRefillPeriod(String refillPeriod) {
        this.refillPeriod = refillPeriod;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTrustProxyHeaders() {
        return trustProxyHeaders;
    }

    public void setTrustProxyHeaders(boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }
}
