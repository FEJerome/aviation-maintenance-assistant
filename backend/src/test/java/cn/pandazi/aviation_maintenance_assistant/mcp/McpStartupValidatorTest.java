package cn.pandazi.aviation_maintenance_assistant.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStartupValidatorTest {

    @Test
    void disabledEndpointSkipsTokenValidation() {
        assertThatCode(() -> validator(new MockEnvironment())
                .run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void amaEntryPointAloneDoesNotArmTheGuardWithoutTheServerSwitch() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("ama.mcp.enabled", "true");

        assertThatCode(() -> validator(environment, null)
                .run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void explicitlyDisabledServerSwitchSkipsValidationEvenWhenAmaEntryPointIsSet() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.mcp.server.enabled", "false")
                .withProperty("ama.mcp.enabled", "true");

        assertThatCode(() -> validator(environment, null)
                .run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void serverSwitchEnabledWithoutTokenFailsClosed() {
        MockEnvironment environment = serverEnabled();

        assertThatThrownBy(() -> validator(environment, null)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AMA_MCP_AUTH_TOKEN");
    }

    @Test
    void serverSwitchEnabledWithAmaEntryPointButNoTokenStillFailsClosed() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.mcp.server.enabled", "true")
                .withProperty("ama.mcp.enabled", "true");

        assertThatThrownBy(() -> validator(environment, null)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankTokenFailsClosed() {
        assertThatThrownBy(() -> validator(serverEnabled(), "   ")
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tooShortTokenFailsClosed() {
        assertThatThrownBy(() -> validator(serverEnabled(), "short-token")
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void minimumLengthTokenIsAccepted() {
        assertThatCode(() -> validator(serverEnabled(), "0123456789abcdef")
                .run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledEndpointWithMalformedPathFailsClosed() {
        McpProperties properties = properties("0123456789abcdef");
        properties.setPath("mcp");

        assertThatThrownBy(() -> new McpStartupValidator(properties, serverEnabled())
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AMA_MCP_PATH");
    }

    private McpStartupValidator validator(MockEnvironment environment) {
        return validator(environment, null);
    }

    private McpStartupValidator validator(MockEnvironment environment, String token) {
        return new McpStartupValidator(properties(token), environment);
    }

    private MockEnvironment serverEnabled() {
        return new MockEnvironment()
                .withProperty("spring.ai.mcp.server.enabled", "true");
    }

    private McpProperties properties(String token) {
        McpProperties properties = new McpProperties();
        properties.setAuthToken(token);
        return properties;
    }
}
