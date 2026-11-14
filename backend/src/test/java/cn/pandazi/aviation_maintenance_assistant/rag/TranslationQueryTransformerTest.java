package cn.pandazi.aviation_maintenance_assistant.rag;

import cn.pandazi.aviation_maintenance_assistant.config.AmaProperties;
import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TranslationQueryTransformerTest {

    private final LlmCallExecutor executor = mock(LlmCallExecutor.class);

    @Test
    void chineseQueryUsesTheExecutorAndReturnsOnlyTheTranslation() {
        when(executor.executeBlocking(any(ChatRequest.class), eq("trace-translate")))
                .thenReturn(response("Synthetic translation"));
        TranslationQueryTransformer transformer = new TranslationQueryTransformer(executor, properties(true));

        Collection<Query> result = transformer.transform(Query.from("合成问题"), "trace-translate");

        assertThat(result).containsExactly(Query.from("Synthetic translation"));
        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(executor).executeBlocking(request.capture(), eq("trace-translate"));
        assertThat(request.getValue().messages()).hasSize(1);
    }

    @Test
    void englishOrDisabledTranslationDoesNotCallTheModel() {
        TranslationQueryTransformer enabled = new TranslationQueryTransformer(executor, properties(true));
        TranslationQueryTransformer disabled = new TranslationQueryTransformer(executor, properties(false));

        assertThat(enabled.transform(Query.from("Synthetic question"), "trace-english"))
                .containsExactly(Query.from("Synthetic question"));
        assertThat(disabled.transform(Query.from("合成问题"), "trace-disabled"))
                .containsExactly(Query.from("合成问题"));
        verifyNoInteractions(executor);
    }

    private AmaProperties properties(boolean enabled) {
        AmaProperties properties = new AmaProperties();
        properties.getRag().setQueryTranslationEnabled(enabled);
        return properties;
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }
}
