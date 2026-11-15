package cn.pandazi.aviation_maintenance_assistant.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard chain must arm exactly when the transport itself is effectively enabled
 * ({@code spring.ai.mcp.server.enabled}), regardless of which property source set it.
 */
class McpAuthFilterRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(McpProperties.class, () -> {
                McpProperties properties = new McpProperties();
                properties.setAuthToken("test-mcp-token-0123456789");
                return properties;
            })
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(McpAuthFilter.McpAuthFilterConfiguration.class);

    @Test
    void filterRegistersWhenTheServerSwitchIsEnabled() {
        contextRunner
                .withPropertyValues("spring.ai.mcp.server.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(FilterRegistrationBean.class));
    }

    @Test
    void filterConditionIgnoresTheAmaEntryPointProperty() {
        // The registration condition reads the effective server switch only; in a real
        // application both switches are joined through the AMA_MCP_ENABLED placeholder.
        contextRunner
                .withPropertyValues("ama.mcp.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void filterDoesNotRegisterWhenTheServerSwitchIsOff() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.mcp.server.enabled=false",
                        "ama.mcp.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void filterDoesNotRegisterByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void registrationTargetsOnlyTheConfiguredMcpPath() {
        contextRunner
                .withPropertyValues("spring.ai.mcp.server.enabled=true")
                .run(context -> {
                    FilterRegistrationBean<?> registration =
                            context.getBean(FilterRegistrationBean.class);
                    assertThat(registration.getUrlPatterns()).containsExactly("/mcp");
                });
    }
}
