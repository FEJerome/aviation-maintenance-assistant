package cn.pandazi.aviation_maintenance_assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置属性
 */
@Component
@ConfigurationProperties(prefix = "rate-limit.chat")
public class RateLimitProperties {

    /**
     * 每个 IP 在 refill-period 内的最大请求次数
     */
    private long capacity = 20;

    /**
     * 令牌桶重置周期，支持 ISO-8601 持续时间简写：
     * 例如 1h、10s、30m
     */
    private String refillPeriod = "1h";

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
}
