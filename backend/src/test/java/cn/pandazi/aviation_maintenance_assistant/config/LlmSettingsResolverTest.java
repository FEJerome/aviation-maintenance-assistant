package cn.pandazi.aviation_maintenance_assistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmSettingsResolverTest {

    private static final String LEGACY_KEY = "legacy-test-credential";

    @Test
    void resolvesGenericSettingsWithoutAddingProtocolPathsOrTemperature() {
        Map<String, String> values = genericSettings();
        values.put("ama.llm.timeout-seconds", "30");
        values.put("ama.llm.streaming-enabled", "false");

        LlmSettingsResolver.ResolvedLlmSettings settings = resolver(values).resolve();

        assertEquals("https://models.example.test/root/v1", settings.baseUrl());
        assertEquals("generic-test-credential", settings.apiKey());
        assertEquals("synthetic-chat", settings.model());
        assertEquals(Duration.ofSeconds(30), settings.timeout());
        assertFalse(settings.streamingEnabled());
        assertNull(settings.temperature());
        assertEquals(LlmSettingsResolver.SettingsSource.GENERIC, settings.source());
    }

    @Test
    void genericSettingsTakePrecedenceAndNeverBorrowLegacyValues() {
        Map<String, String> values = genericSettings();
        values.put("DEEPSEEK_API_KEY", LEGACY_KEY);
        values.put("LANGCHAIN4J_OPEN_AI_CHAT_MODEL_API_KEY", "unselected-test-credential");

        LlmSettingsResolver.ResolvedLlmSettings settings = resolver(values).resolve();

        assertEquals("generic-test-credential", settings.apiKey());
        assertEquals("synthetic-chat", settings.model());
        assertNull(settings.temperature());
        assertEquals(LlmSettingsResolver.SettingsSource.GENERIC, settings.source());
    }

    @Test
    void incompleteGenericSettingsFailEvenWhenLegacyKeyExists() {
        List<Map<String, String>> incompleteConfigurations = List.of(
                configuration("ama.llm.base-url", "https://models.example.test/v1"),
                configuration("ama.llm.model", "synthetic-chat"),
                configuration("ama.llm.api-key", "generic-test-credential"),
                configuration(
                        "ama.llm.base-url", "https://models.example.test/v1",
                        "ama.llm.model", "synthetic-chat"),
                configuration(
                        "ama.llm.base-url", "https://models.example.test/v1",
                        "ama.llm.api-key", "generic-test-credential"),
                configuration(
                        "ama.llm.model", "synthetic-chat",
                        "ama.llm.api-key", "generic-test-credential"),
                configuration("ama.llm.base-url", "")
        );

        for (Map<String, String> values : incompleteConfigurations) {
            values.put("DEEPSEEK_API_KEY", LEGACY_KEY);
            assertThrows(IllegalArgumentException.class, () -> resolver(values).resolve());
        }
    }

    @Test
    void resolvesOnlyTheApprovedLegacyMapping() {
        LlmSettingsResolver.ResolvedLlmSettings settings = resolver(
                configuration("DEEPSEEK_API_KEY", LEGACY_KEY)).resolve();

        assertEquals("https://api.deepseek.com/v1", settings.baseUrl());
        assertEquals(LEGACY_KEY, settings.apiKey());
        assertEquals("deepseek-chat", settings.model());
        assertEquals(0.2, settings.temperature());
        assertEquals(LlmSettingsResolver.SettingsSource.DEEPSEEK_LEGACY, settings.source());
    }

    @Test
    void localOnlyRejectsLegacyCloudFallbackAndAllowsKeylessGenericEndpoint() {
        Map<String, String> legacy = configuration(
                "ama.runtime-mode", "local-only",
                "DEEPSEEK_API_KEY", LEGACY_KEY);
        assertThrows(IllegalArgumentException.class, () -> resolver(legacy).resolve());

        Map<String, String> local = configuration(
                "ama.runtime-mode", "local-only",
                "ama.llm.base-url", "http://localhost:11434/v1/",
                "ama.llm.model", "local-synthetic-chat");
        LlmSettingsResolver.ResolvedLlmSettings settings = resolver(local).resolve();

        assertNull(settings.apiKey());
        assertEquals("http://localhost:11434/v1", settings.baseUrl());
    }

    @Test
    void ignoresTheUnapprovedLangChainKeySource() {
        assertThrows(IllegalArgumentException.class, () -> resolver(configuration(
                "LANGCHAIN4J_OPEN_AI_CHAT_MODEL_API_KEY", "unselected-test-credential")).resolve());
    }

    @Test
    void rejectsMissingConfigurationInvalidTimeoutsAndNonFiniteTemperature() {
        assertThrows(IllegalArgumentException.class, () -> resolver(Map.of()).resolve());

        for (String timeout : List.of("0", "-1")) {
            Map<String, String> values = genericSettings();
            values.put("ama.llm.timeout-seconds", timeout);
            assertThrows(IllegalArgumentException.class, () -> resolver(values).resolve());
        }

        Map<String, String> invalidTemperature = genericSettings();
        invalidTemperature.put("ama.llm.temperature", "NaN");
        assertThrows(IllegalArgumentException.class, () -> resolver(invalidTemperature).resolve());
    }

    @Test
    void resolvedSettingsNeverExposeTheCredentialThroughToString() {
        Map<String, String> values = genericSettings();
        values.put("ama.llm.api-key", "credential-marker-that-must-stay-hidden");

        String rendered = resolver(values).resolve().toString();

        assertFalse(rendered.contains("credential-marker-that-must-stay-hidden"));
        assertTrue(rendered.contains("<redacted>"));
    }

    private Map<String, String> genericSettings() {
        return configuration(
                "ama.llm.base-url", "https://models.example.test/root/v1///",
                "ama.llm.api-key", "generic-test-credential",
                "ama.llm.model", "synthetic-chat");
    }

    private Map<String, String> configuration(String... entries) {
        Map<String, String> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return values;
    }

    private LlmSettingsResolver resolver(Map<String, String> values) {
        MockEnvironment environment = new MockEnvironment();
        values.forEach(environment::setProperty);
        AmaProperties properties = Binder.get(environment)
                .bind("ama", Bindable.of(AmaProperties.class))
                .orElseGet(AmaProperties::new);
        LocalOnlyEndpointGuard endpointGuard = new LocalOnlyEndpointGuard(
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        return new LlmSettingsResolver(properties, environment, endpointGuard);
    }
}
