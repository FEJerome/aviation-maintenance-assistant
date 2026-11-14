package cn.pandazi.aviation_maintenance_assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Provider-neutral settings for model access and its local runtime state.
 */
@Component
@ConfigurationProperties(prefix = "ama")
public class AmaProperties {

    public enum RuntimeMode {
        CLOUD_ALLOWED,
        LOCAL_ONLY
    }

    private RuntimeMode runtimeMode = RuntimeMode.CLOUD_ALLOWED;
    private String statePath = "./state";
    private String dataPath = "./data";
    private Llm llm = new Llm();
    private Chroma chroma = new Chroma();
    private Rag rag = new Rag();

    public RuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    public void setRuntimeMode(RuntimeMode runtimeMode) {
        this.runtimeMode = runtimeMode;
    }

    public String getStatePath() {
        return statePath;
    }

    public void setStatePath(String statePath) {
        this.statePath = statePath;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public Chroma getChroma() {
        return chroma;
    }

    public void setChroma(Chroma chroma) {
        this.chroma = chroma;
    }

    public Rag getRag() {
        return rag;
    }

    public void setRag(Rag rag) {
        this.rag = rag;
    }

    public static class Llm {

        private String baseUrl;
        private String apiKey;
        private String model;
        private int timeoutSeconds = 60;
        private boolean streamingEnabled = true;
        private Double temperature;
        private boolean quotaEnabled = true;
        private int dailyQuota = 500;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isStreamingEnabled() {
            return streamingEnabled;
        }

        public void setStreamingEnabled(boolean streamingEnabled) {
            this.streamingEnabled = streamingEnabled;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public boolean isQuotaEnabled() {
            return quotaEnabled;
        }

        public void setQuotaEnabled(boolean quotaEnabled) {
            this.quotaEnabled = quotaEnabled;
        }

        public int getDailyQuota() {
            return dailyQuota;
        }

        public void setDailyQuota(int dailyQuota) {
            this.dailyQuota = dailyQuota;
        }
    }

    public static class Rag {

        private int maxResults = 5;
        private double minScore = 0.6;
        private boolean queryTranslationEnabled = true;

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public boolean isQueryTranslationEnabled() {
            return queryTranslationEnabled;
        }

        public void setQueryTranslationEnabled(boolean queryTranslationEnabled) {
            this.queryTranslationEnabled = queryTranslationEnabled;
        }
    }

    public static class Chroma {

        private String baseUrl = "http://localhost:8000";
        private String collection = "aviation-docs";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }
    }
}
