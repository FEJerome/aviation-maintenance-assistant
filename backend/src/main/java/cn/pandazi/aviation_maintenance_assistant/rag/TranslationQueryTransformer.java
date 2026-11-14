package cn.pandazi.aviation_maintenance_assistant.rag;

import cn.pandazi.aviation_maintenance_assistant.config.AmaProperties;
import cn.pandazi.aviation_maintenance_assistant.service.LlmCallExecutor;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.rag.query.Query;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;

@Component
public class TranslationQueryTransformer implements RetrievalService.TracedQueryTransformer {

    private static final String TRANSLATION_PROMPT_TEMPLATE = """
            You are an aviation maintenance translation specialist.
            Translate the user's question into English for retrieving maintenance manuals.
            Return only the translation, without explanations or additional text.

            User question: %s
            """;

    private final LlmCallExecutor callExecutor;
    private final boolean enabled;

    public TranslationQueryTransformer(LlmCallExecutor callExecutor, AmaProperties properties) {
        this.callExecutor = callExecutor;
        this.enabled = properties.getRag().isQueryTranslationEnabled();
    }

    @Override
    public Collection<Query> transform(Query query, String traceId) {
        if (!enabled || !containsChinese(query.text())) {
            return Collections.singletonList(query);
        }

        String prompt = String.format(TRANSLATION_PROMPT_TEMPLATE, query.text());
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build();
        String translatedText = callExecutor.executeBlocking(request, traceId)
                .aiMessage()
                .text()
                .trim();
        if (translatedText.startsWith("\"") && translatedText.endsWith("\"")) {
            translatedText = translatedText.substring(1, translatedText.length() - 1);
        }
        return Collections.singletonList(Query.from(translatedText));
    }

    private boolean containsChinese(String text) {
        return text.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                        || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                        || (codePoint >= 0xF900 && codePoint <= 0xFAFF));
    }
}
