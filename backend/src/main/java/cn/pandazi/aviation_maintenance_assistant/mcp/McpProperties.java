package cn.pandazi.aviation_maintenance_assistant.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Read-only MCP endpoint guard settings. Disabled by default; enabling it without a
 * sufficiently long auth token fails closed at startup (see McpStartupValidator).
 */
@Component
@ConfigurationProperties(prefix = "ama.mcp")
public class McpProperties {

    private boolean enabled = false;
    private String authToken;
    private String path = "/mcp";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
