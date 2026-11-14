package cn.pandazi.aviation_maintenance_assistant.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiConfig {

    @Bean
    public LlmSettingsResolver.ResolvedLlmSettings resolvedLlmSettings(LlmSettingsResolver resolver) {
        return resolver.resolve();
    }

    @Bean
    public ChatModel chatModel(LlmSettingsResolver.ResolvedLlmSettings settings) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(settings.baseUrl())
                .modelName(settings.model())
                .timeout(settings.timeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);
        if (settings.apiKey() != null) {
            builder.apiKey(settings.apiKey());
        }
        if (settings.temperature() != null) {
            builder.temperature(settings.temperature());
        }
        return builder.build();
    }

    @Bean
    public StreamingChatModel streamingChatModel(LlmSettingsResolver.ResolvedLlmSettings settings) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .baseUrl(settings.baseUrl())
                .modelName(settings.model())
                .timeout(settings.timeout())
                .logRequests(false)
                .logResponses(false);
        if (settings.apiKey() != null) {
            builder.apiKey(settings.apiKey());
        }
        if (settings.temperature() != null) {
            builder.temperature(settings.temperature());
        }
        return builder.build();
    }
}
