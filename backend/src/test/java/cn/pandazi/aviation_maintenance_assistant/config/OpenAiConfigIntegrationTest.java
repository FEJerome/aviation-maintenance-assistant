package cn.pandazi.aviation_maintenance_assistant.config;

import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import cn.pandazi.aviation_maintenance_assistant.service.LlmQuotaService;
import cn.pandazi.aviation_maintenance_assistant.support.FakeOpenAiServer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OpenAiConfigIntegrationTest {

    private FakeOpenAiServer server;
    private final OpenAiConfig config = new OpenAiConfig();

    @BeforeEach
    void startServer() throws Exception {
        server = new FakeOpenAiServer();
    }

    @AfterEach
    void closeServer() {
        server.close();
    }

    @Test
    void blockingClientUsesExactPathModelAndOmitsUnsetTemperature() {
        ChatModel model = config.chatModel(settings(null));

        String answer = model.chat("Synthetic question");

        assertThat(answer).isEqualTo("Synthetic answer");
        assertThat(server.calls()).isEqualTo(1);
        assertThat(server.lastPath()).isEqualTo("/v1/chat/completions");
        assertThat(server.lastRequest().path("model").asText()).isEqualTo("synthetic-chat");
        assertThat(server.lastRequest().has("temperature")).isFalse();
        assertThat(server.lastRequest().path("stream").asBoolean(false)).isFalse();
        assertThat(server.lastAuthorization()).isEqualTo("Bearer test-credential");
    }

    @Test
    void configuredTemperatureIsSentExplicitly() {
        ChatModel model = config.chatModel(settings("0.35"));

        model.chat("Synthetic question");

        assertThat(server.lastRequest().path("temperature").asDouble()).isEqualTo(0.35);
    }

    @Test
    void streamingClientUsesOneStreamingRequest() throws Exception {
        StreamingChatModel model = config.streamingChatModel(settings(null));
        CountDownLatch completed = new CountDownLatch(1);
        StringBuilder tokens = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();

        model.chat("Synthetic question", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                tokens.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completed.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                completed.countDown();
            }
        });

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNull();
        assertThat(tokens.toString()).isEqualTo("Synthetic stream");
        assertThat(server.lastRequest().path("stream").asBoolean()).isTrue();
        assertThat(server.calls()).isEqualTo(1);
    }

    @Test
    void rejectedBlockingCallIsNotRetriedByClientOrExecutor() {
        ChatModel model = config.chatModel(settings(null));
        LlmCallExecutor executor = new LlmCallExecutor(
                model,
                config.streamingChatModel(settings(null)),
                mock(LlmQuotaService.class));
        server.setStatus(400);

        assertThatThrownBy(() -> executor.executeBlocking(
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("Synthetic question"))
                        .build(),
                "trace-fake"))
                .isInstanceOf(LlmCallExecutor.LlmCallException.class)
                .extracting("code")
                .isEqualTo(LlmCallExecutor.ErrorCode.LLM_PROTOCOL_ERROR);
        assertThat(server.calls()).isEqualTo(1);
    }

    @Test
    void rejectedStreamingCallStopsAfterOneRequestWithoutBlockingFallback() throws Exception {
        ChatModel blocking = mock(ChatModel.class);
        StreamingChatModel streaming = config.streamingChatModel(settings(null));
        LlmCallExecutor executor = new LlmCallExecutor(
                blocking, streaming, mock(LlmQuotaService.class));
        server.setStatus(400);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        executor.executeStreaming(
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("Synthetic question"))
                        .build(),
                "trace-fake-stream",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        completed.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error.set(throwable);
                        completed.countDown();
                    }
                });

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isInstanceOf(LlmCallExecutor.LlmCallException.class);
        assertThat(((LlmCallExecutor.LlmCallException) error.get()).code())
                .isEqualTo(LlmCallExecutor.ErrorCode.LLM_STREAMING_UNSUPPORTED);
        assertThat(server.calls()).isEqualTo(1);
        org.mockito.Mockito.verifyNoInteractions(blocking);
    }

    private LlmSettingsResolver.ResolvedLlmSettings settings(String temperature) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("ama.llm.base-url", server.baseUrl())
                .withProperty("ama.llm.api-key", "test-credential")
                .withProperty("ama.llm.model", "synthetic-chat");
        if (temperature != null) {
            environment.setProperty("ama.llm.temperature", temperature);
        }
        AmaProperties properties = Binder.get(environment)
                .bind("ama", Bindable.of(AmaProperties.class))
                .orElseGet(AmaProperties::new);
        LocalOnlyEndpointGuard guard = new LocalOnlyEndpointGuard(
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        return new LlmSettingsResolver(properties, environment, guard).resolve();
    }
}
