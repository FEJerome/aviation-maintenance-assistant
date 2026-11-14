package cn.pandazi.aviation_maintenance_assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/**
 * Resolves generic model settings and the explicitly supported legacy configuration.
 */
@Component
public class LlmSettingsResolver {

    public enum SettingsSource {
        GENERIC,
        DEEPSEEK_LEGACY
    }

    private static final Logger log = LoggerFactory.getLogger(LlmSettingsResolver.class);
    private static final String BASE_URL_PROPERTY = "ama.llm.base-url";
    private static final String API_KEY_PROPERTY = "ama.llm.api-key";
    private static final String MODEL_PROPERTY = "ama.llm.model";
    private static final String LEGACY_API_KEY_PROPERTY = "DEEPSEEK_API_KEY";
    private static final String LEGACY_BASE_URL = "https://api.deepseek.com/v1";
    private static final String LEGACY_MODEL = "deepseek-chat";
    private static final double LEGACY_TEMPERATURE = 0.2;

    private final AmaProperties properties;
    private final Environment environment;
    private final LocalOnlyEndpointGuard endpointGuard;

    public LlmSettingsResolver(
            AmaProperties properties,
            Environment environment,
            LocalOnlyEndpointGuard endpointGuard) {
        this.properties = properties;
        this.environment = environment;
        this.endpointGuard = endpointGuard;
    }

    public ResolvedLlmSettings resolve() {
        AmaProperties.RuntimeMode runtimeMode = properties.getRuntimeMode();
        if (runtimeMode == null) {
            throw new IllegalArgumentException("Runtime mode must be configured");
        }

        boolean genericSelected = environment.containsProperty(BASE_URL_PROPERTY)
                || environment.containsProperty(API_KEY_PROPERTY)
                || environment.containsProperty(MODEL_PROPERTY);
        String legacyApiKey = environment.getProperty(LEGACY_API_KEY_PROPERTY);
        boolean legacySelected = legacyApiKey != null;

        AmaProperties.Llm configured = properties.getLlm();
        String baseUrl;
        String apiKey;
        String model;
        Double temperature;
        SettingsSource source;

        if (genericSelected) {
            baseUrl = requireText(configured.getBaseUrl(), "Generic LLM base URL must be configured");
            model = requireText(configured.getModel(), "Generic LLM model must be configured");
            apiKey = configured.getApiKey();
            if (runtimeMode == AmaProperties.RuntimeMode.CLOUD_ALLOWED) {
                requireText(apiKey, "Generic LLM API key must be configured in cloud-allowed mode");
            } else if (apiKey != null && apiKey.isBlank()) {
                apiKey = null;
            }
            temperature = configured.getTemperature();
            source = SettingsSource.GENERIC;
        } else if (legacySelected) {
            if (runtimeMode == AmaProperties.RuntimeMode.LOCAL_ONLY) {
                throw new IllegalArgumentException(
                        "Legacy cloud settings cannot be used in local-only mode");
            }
            apiKey = requireText(legacyApiKey, "Legacy LLM API key must not be blank");
            baseUrl = LEGACY_BASE_URL;
            model = LEGACY_MODEL;
            temperature = LEGACY_TEMPERATURE;
            source = SettingsSource.DEEPSEEK_LEGACY;
            log.warn("Legacy LLM configuration is deprecated; migrate to generic LLM settings");
        } else {
            throw new IllegalArgumentException("Generic LLM connection settings must be configured");
        }

        if (configured.getTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException("LLM timeout must be a positive number of seconds");
        }
        if (temperature != null && !Double.isFinite(temperature)) {
            throw new IllegalArgumentException("LLM temperature must be finite");
        }

        URI validatedLlmUri = endpointGuard.validateLlm(baseUrl, runtimeMode);
        endpointGuard.validateChroma(properties.getChroma().getBaseUrl(), runtimeMode);

        return new ResolvedLlmSettings(
                validatedLlmUri.toString(),
                apiKey,
                model,
                Duration.ofSeconds(configured.getTimeoutSeconds()),
                configured.isStreamingEnabled(),
                temperature,
                source);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public static final class ResolvedLlmSettings {

        private final String baseUrl;
        private final String apiKey;
        private final String model;
        private final Duration timeout;
        private final boolean streamingEnabled;
        private final Double temperature;
        private final SettingsSource source;

        private ResolvedLlmSettings(
                String baseUrl,
                String apiKey,
                String model,
                Duration timeout,
                boolean streamingEnabled,
                Double temperature,
                SettingsSource source) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.timeout = timeout;
            this.streamingEnabled = streamingEnabled;
            this.temperature = temperature;
            this.source = source;
        }

        public String baseUrl() {
            return baseUrl;
        }

        public String apiKey() {
            return apiKey;
        }

        public String model() {
            return model;
        }

        public Duration timeout() {
            return timeout;
        }

        public boolean streamingEnabled() {
            return streamingEnabled;
        }

        public Double temperature() {
            return temperature;
        }

        public SettingsSource source() {
            return source;
        }

        @Override
        public String toString() {
            return "ResolvedLlmSettings["
                    + "baseUrl=" + baseUrl
                    + ", apiKey=" + (apiKey == null ? "<none>" : "<redacted>")
                    + ", model=" + model
                    + ", timeout=" + timeout
                    + ", streamingEnabled=" + streamingEnabled
                    + ", temperature=" + temperature
                    + ", source=" + source
                    + ']';
        }
    }
}
