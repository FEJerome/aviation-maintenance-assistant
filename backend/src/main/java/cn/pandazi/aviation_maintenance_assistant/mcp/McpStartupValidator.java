package cn.pandazi.aviation_maintenance_assistant.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fails closed at startup when the read-only MCP endpoint is enabled without a usable
 * auth token. The token value itself is never logged.
 */
@Component
public class McpStartupValidator implements ApplicationRunner {

    static final int MIN_AUTH_TOKEN_LENGTH = 16;

    private static final Logger log = LoggerFactory.getLogger(McpStartupValidator.class);

    private final McpProperties properties;

    public McpStartupValidator(McpProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        String token = properties.getAuthToken();
        if (token == null || token.isBlank() || token.length() < MIN_AUTH_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "ama.mcp.auth-token (AMA_MCP_AUTH_TOKEN) must be at least "
                            + MIN_AUTH_TOKEN_LENGTH
                            + " characters when the MCP endpoint is enabled.");
        }
        String path = properties.getPath();
        if (path == null || !path.startsWith("/") || path.length() < 2) {
            throw new IllegalStateException(
                    "ama.mcp.path (AMA_MCP_PATH) must start with '/' when the MCP endpoint is enabled.");
        }
        log.info("MCP endpoint enabled on {} (loopback-only, token-protected).", path);
    }
}
