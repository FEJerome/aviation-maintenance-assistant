package cn.pandazi.aviation_maintenance_assistant.service;

import cn.pandazi.aviation_maintenance_assistant.config.AmaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Reserves model-call attempts against a single-process, UTC-daily quota.
 */
@Service
public class LlmQuotaService {

    private static final String QUOTA_FILE_NAME = "daily-llm-quota.json";

    @FunctionalInterface
    interface AtomicReplace {
        void replace(Path source, Path target) throws IOException;
    }

    private final Path quotaFile;
    private final int dailyLimit;
    private final boolean enabled;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final AtomicReplace atomicReplace;

    private boolean loaded;
    private LocalDate currentDate;
    private int count;

    @Autowired
    public LlmQuotaService(AmaProperties properties) {
        this(
                stateFile(properties.getStatePath()),
                properties.getLlm().getDailyQuota(),
                properties.getLlm().isQuotaEnabled(),
                Clock.systemUTC(),
                new ObjectMapper(),
                LlmQuotaService::moveAtomically);
    }

    LlmQuotaService(
            Path quotaFile,
            int dailyLimit,
            boolean enabled,
            Clock clock,
            ObjectMapper objectMapper) {
        this(quotaFile, dailyLimit, enabled, clock, objectMapper, LlmQuotaService::moveAtomically);
    }

    LlmQuotaService(
            Path quotaFile,
            int dailyLimit,
            boolean enabled,
            Clock clock,
            ObjectMapper objectMapper,
            AtomicReplace atomicReplace) {
        if (dailyLimit <= 0) {
            throw new IllegalArgumentException("Daily LLM quota must be positive");
        }
        this.quotaFile = quotaFile;
        this.dailyLimit = dailyLimit;
        this.enabled = enabled;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.atomicReplace = atomicReplace;
    }

    /**
     * Reserves one attempt. A successful reservation is persisted before the model is called.
     */
    public synchronized void acquire() {
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        if (!loaded) {
            load(today);
            loaded = true;
        }
        if (!today.equals(currentDate)) {
            currentDate = today;
            count = 0;
        }
        if (count >= dailyLimit) {
            throw new QuotaExhaustedException();
        }

        int reservedCount = count + 1;
        persist(today, reservedCount);
        count = reservedCount;
    }

    private void load(LocalDate today) {
        currentDate = today;
        count = 0;
        if (!Files.exists(quotaFile)) {
            return;
        }

        try {
            QuotaRecord record = objectMapper.readValue(quotaFile.toFile(), QuotaRecord.class);
            if (record == null || record.date() == null || record.count() < 0) {
                throw new IOException("Invalid quota record");
            }
            LocalDate persistedDate = LocalDate.parse(record.date());
            if (today.equals(persistedDate)) {
                count = record.count();
            }
        } catch (Exception exception) {
            throw new QuotaStateUnavailableException();
        }
    }

    private void persist(LocalDate today, int reservedCount) {
        Path parent = quotaFile.getParent();
        Path temporaryFile = null;
        boolean replaced = false;
        try {
            if (parent == null) {
                throw new IOException("Quota file must have a parent directory");
            }
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, QUOTA_FILE_NAME + ".", ".tmp");
            objectMapper.writeValue(
                    temporaryFile.toFile(),
                    new QuotaRecord(today.toString(), reservedCount));
            try (FileChannel channel = FileChannel.open(temporaryFile, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            atomicReplace.replace(temporaryFile, quotaFile);
            replaced = true;
        } catch (Exception exception) {
            throw new QuotaStateUnavailableException();
        } finally {
            if (!replaced && temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // The original state failure remains the relevant outcome.
                }
            }
        }
    }

    private static Path stateFile(String statePath) {
        if (statePath == null || statePath.isBlank()) {
            throw new IllegalArgumentException("State path must be configured");
        }
        return Path.of(statePath).resolve(QUOTA_FILE_NAME);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    public record QuotaRecord(String date, int count) {
    }

    public static class QuotaExhaustedException extends RuntimeException {
        public QuotaExhaustedException() {
            super("Daily LLM quota is exhausted");
        }
    }

    public static class QuotaStateUnavailableException extends RuntimeException {
        public QuotaStateUnavailableException() {
            super("LLM quota state is unavailable");
        }
    }
}
