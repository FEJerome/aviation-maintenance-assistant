package cn.pandazi.aviation_maintenance_assistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmaPropertiesTest {

    @Test
    void defaultsPreserveExistingLimitsWithoutChoosingAProvider() {
        AmaProperties properties = new AmaProperties();

        assertEquals(AmaProperties.RuntimeMode.CLOUD_ALLOWED, properties.getRuntimeMode());
        assertEquals("./state", properties.getStatePath());
        assertNull(properties.getLlm().getBaseUrl());
        assertNull(properties.getLlm().getApiKey());
        assertNull(properties.getLlm().getModel());
        assertNull(properties.getLlm().getTemperature());
        assertEquals(60, properties.getLlm().getTimeoutSeconds());
        assertTrue(properties.getLlm().isStreamingEnabled());
        assertEquals("http://localhost:8000", properties.getChroma().getBaseUrl());
        assertEquals("aviation-docs", properties.getChroma().getCollection());
        assertTrue(properties.getLlm().isQuotaEnabled());
        assertEquals(500, properties.getLlm().getDailyQuota());
    }

    @Test
    void bindsEnvironmentStyleNamesAndOptionalValues() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("AMA_RUNTIME_MODE", "local-only");
        variables.put("AMA_STATE_PATH", "build/test-state");
        variables.put("AMA_LLM_BASE_URL", "http://localhost:11434/v1/");
        variables.put("AMA_LLM_API_KEY", "test-credential");
        variables.put("AMA_LLM_MODEL", "synthetic-chat");
        variables.put("AMA_LLM_TIMEOUT_SECONDS", "45");
        variables.put("AMA_LLM_STREAMING_ENABLED", "false");
        variables.put("AMA_LLM_TEMPERATURE", "0.35");
        variables.put("AMA_CHROMA_BASE_URL", "http://chromadb:8000");
        variables.put("AMA_CHROMA_COLLECTION", "synthetic-documents");
        variables.put("AMA_LLM_QUOTA_ENABLED", "false");
        variables.put("AMA_LLM_DAILY_QUOTA", "42");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));

        AmaProperties properties = Binder.get(environment)
                .bind("ama", Bindable.of(AmaProperties.class))
                .orElseThrow(() -> new AssertionError("Expected AMA properties to be bound"));

        assertEquals(AmaProperties.RuntimeMode.LOCAL_ONLY, properties.getRuntimeMode());
        assertEquals("build/test-state", properties.getStatePath());
        assertEquals("http://localhost:11434/v1/", properties.getLlm().getBaseUrl());
        assertEquals("test-credential", properties.getLlm().getApiKey());
        assertEquals("synthetic-chat", properties.getLlm().getModel());
        assertEquals(45, properties.getLlm().getTimeoutSeconds());
        assertFalse(properties.getLlm().isStreamingEnabled());
        assertEquals(0.35, properties.getLlm().getTemperature());
        assertEquals("http://chromadb:8000", properties.getChroma().getBaseUrl());
        assertEquals("synthetic-documents", properties.getChroma().getCollection());
        assertFalse(properties.getLlm().isQuotaEnabled());
        assertEquals(42, properties.getLlm().getDailyQuota());
    }
}
