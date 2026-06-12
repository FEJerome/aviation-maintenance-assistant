package cn.pandazi.aviation_maintenance_assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DeepSeek 全局日限额服务
 * <p>
 * 1. 限制每天 DeepSeek 总调用次数。
 * 2. 计数持久化到本地文件，重启后恢复。
 * 3. 每天 0 点自动重置。
 */
@Service
public class DeepSeekQuotaService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekQuotaService.class);

    @Value("${deepseek.quota.daily-limit:500}")
    private int dailyLimit;

    private final AtomicInteger dailyCounter = new AtomicInteger(0);
    private final Path quotaFile = Paths.get("data", "daily-llm-quota.json");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        loadQuota();
    }

    public boolean isExhausted() {
        return dailyCounter.get() >= dailyLimit;
    }

    /**
     * 尝试获取一次调用额度。
     *
     * @return 如果当前调用未超过日限额返回 true；否则返回 false
     */
    public boolean tryAcquire() {
        int current = dailyCounter.incrementAndGet();
        saveQuota();
        return current <= dailyLimit;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDailyQuota() {
        dailyCounter.set(0);
        saveQuota();
        log.info("Daily DeepSeek quota has been reset to 0");
    }

    private void loadQuota() {
        try {
            if (Files.exists(quotaFile)) {
                String content = Files.readString(quotaFile);
                QuotaRecord record = objectMapper.readValue(content, QuotaRecord.class);
                String today = LocalDate.now().toString();
                if (today.equals(record.date())) {
                    dailyCounter.set(record.count());
                    log.info("Loaded DeepSeek daily quota: {}/{} for {}", record.count(), dailyLimit, today);
                } else {
                    dailyCounter.set(0);
                    saveQuota();
                    log.info("Quota file is outdated, reset DeepSeek daily quota to 0");
                }
            } else {
                dailyCounter.set(0);
                saveQuota();
            }
        } catch (Exception e) {
            log.error("Failed to load quota file, resetting to 0", e);
            dailyCounter.set(0);
            saveQuota();
        }
    }

    private void saveQuota() {
        try {
            Files.createDirectories(quotaFile.getParent());
            QuotaRecord record = new QuotaRecord(LocalDate.now().toString(), dailyCounter.get());
            objectMapper.writeValue(quotaFile.toFile(), record);
        } catch (Exception e) {
            log.error("Failed to save quota file", e);
        }
    }

    public record QuotaRecord(String date, int count) {
    }
}
