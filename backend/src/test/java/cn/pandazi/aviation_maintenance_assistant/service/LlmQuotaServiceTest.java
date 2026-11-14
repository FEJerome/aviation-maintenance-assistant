package cn.pandazi.aviation_maintenance_assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmQuotaServiceTest {

    private static final Clock FIRST_DAY = Clock.fixed(
            Instant.parse("2026-01-01T23:59:00Z"), ZoneOffset.UTC);
    private static final Clock SECOND_DAY = Clock.fixed(
            Instant.parse("2026-01-02T00:01:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void constructionAndDisabledQuotaDoNotTouchState() {
        Path stateFile = tempDir.resolve("nested/state/daily-llm-quota.json");
        LlmQuotaService disabled = service(stateFile, 2, false, FIRST_DAY);
        service(tempDir.resolve("unused/daily-llm-quota.json"), 2, true, FIRST_DAY);

        disabled.acquire();
        disabled.acquire();

        assertFalse(Files.exists(stateFile));
        assertFalse(Files.exists(tempDir.resolve("nested")));
        assertFalse(Files.exists(tempDir.resolve("unused")));
    }

    @Test
    void enforcesTheLimitWithoutIncrementingRejectedAttempts() throws Exception {
        Path stateFile = stateFile();
        LlmQuotaService service = service(stateFile, 2, true, FIRST_DAY);

        service.acquire();
        service.acquire();
        assertThrows(LlmQuotaService.QuotaExhaustedException.class, service::acquire);

        LlmQuotaService.QuotaRecord record = readRecord(stateFile);
        assertEquals("2026-01-01", record.date());
        assertEquals(2, record.count());
    }

    @Test
    void restoresTheSameUtcDayAndRollsOverOnTheNextUtcDay() throws Exception {
        Path stateFile = stateFile();
        service(stateFile, 1, true, FIRST_DAY).acquire();

        LlmQuotaService restartedSameDay = service(stateFile, 1, true, FIRST_DAY);
        assertThrows(LlmQuotaService.QuotaExhaustedException.class, restartedSameDay::acquire);

        LlmQuotaService restartedNextDay = service(stateFile, 1, true, SECOND_DAY);
        restartedNextDay.acquire();

        LlmQuotaService.QuotaRecord record = readRecord(stateFile);
        assertEquals("2026-01-02", record.date());
        assertEquals(1, record.count());
    }

    @Test
    void concurrentReservationsNeverExceedTheLimit() throws Exception {
        Path stateFile = stateFile();
        LlmQuotaService service = service(stateFile, 12, true, FIRST_DAY);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            tasks.add(() -> {
                try {
                    service.acquire();
                    return true;
                } catch (LlmQuotaService.QuotaExhaustedException exception) {
                    return false;
                }
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        int successfulReservations = 0;
        try {
            List<Future<Boolean>> results = executor.invokeAll(tasks);
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulReservations++;
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(12, successfulReservations);
        assertEquals(12, readRecord(stateFile).count());
    }

    @Test
    void corruptStateFailsClosedWithoutResettingTheFile() throws Exception {
        Path stateFile = stateFile();
        Files.createDirectories(stateFile.getParent());
        Files.writeString(stateFile, "not valid quota state");

        LlmQuotaService service = service(stateFile, 5, true, FIRST_DAY);

        assertThrows(LlmQuotaService.QuotaStateUnavailableException.class, service::acquire);
        assertEquals("not valid quota state", Files.readString(stateFile));
    }

    @Test
    void unavailableStateDirectoryFailsClosed() {
        Path blockingFile = tempDir.resolve("blocking-file");
        assertDoesNotThrowIo(() -> Files.writeString(blockingFile, "not a directory"));
        Path stateFile = blockingFile.resolve("daily-llm-quota.json");
        LlmQuotaService service = service(stateFile, 5, true, FIRST_DAY);

        assertThrows(LlmQuotaService.QuotaStateUnavailableException.class, service::acquire);
    }

    @Test
    void atomicReplacementFailureFailsClosedAndCleansTemporaryFiles() throws Exception {
        Path stateFile = stateFile();
        LlmQuotaService service = new LlmQuotaService(
                stateFile,
                5,
                true,
                FIRST_DAY,
                objectMapper,
                (source, target) -> {
                    throw new AtomicMoveNotSupportedException(
                            source.toString(), target.toString(), "synthetic replacement failure");
                });

        assertThrows(LlmQuotaService.QuotaStateUnavailableException.class, service::acquire);
        assertFalse(Files.exists(stateFile));
        try (Stream<Path> files = Files.list(stateFile.getParent())) {
            assertEquals(0, files.count());
        }
    }

    private Path stateFile() {
        return tempDir.resolve("state/daily-llm-quota.json");
    }

    private LlmQuotaService service(
            Path stateFile, int dailyLimit, boolean enabled, Clock clock) {
        return new LlmQuotaService(stateFile, dailyLimit, enabled, clock, objectMapper);
    }

    private LlmQuotaService.QuotaRecord readRecord(Path stateFile) throws Exception {
        return objectMapper.readValue(stateFile.toFile(), LlmQuotaService.QuotaRecord.class);
    }

    private void assertDoesNotThrowIo(IoAction action) {
        try {
            action.run();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws Exception;
    }
}
