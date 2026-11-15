package cn.pandazi.aviation_maintenance_assistant.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStartupValidatorTest {

    @Test
    void disabledEndpointSkipsTokenValidation() {
        McpProperties properties = new McpProperties();
        properties.setEnabled(false);

        assertThatCode(() -> new McpStartupValidator(properties)
                .run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledEndpointWithoutTokenFailsClosed() {
        McpProperties properties = new McpProperties();
        properties.setEnabled(true);

        assertThatThrownBy(() -> new McpStartupValidator(properties)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AMA_MCP_AUTH_TOKEN");
    }

    @Test
    void blankTokenFailsClosed() {
        McpProperties properties = enabledWithToken("   ");

        assertThatThrownBy(() -> new McpStartupValidator(properties)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tooShortTokenFailsClosed() {
        McpProperties properties = enabledWithToken("short-token");

        assertThatThrownBy(() -> new McpStartupValidator(properties)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void minimumLengthTokenIsAccepted() {
        McpProperties properties = enabledWithToken("0123456789abcdef");

        assertThatCode(() -> new McpStartupValidator(properties)
                .run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledEndpointWithMalformedPathFailsClosed() {
        McpProperties properties = enabledWithToken("0123456789abcdef");
        properties.setPath("mcp");

        assertThatThrownBy(() -> new McpStartupValidator(properties)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AMA_MCP_PATH");
    }

    private McpProperties enabledWithToken(String token) {
        McpProperties properties = new McpProperties();
        properties.setEnabled(true);
        properties.setAuthToken(token);
        return properties;
    }
}
