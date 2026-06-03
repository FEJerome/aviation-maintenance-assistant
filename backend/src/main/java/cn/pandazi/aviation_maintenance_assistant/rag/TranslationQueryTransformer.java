package cn.pandazi.aviation_maintenance_assistant.rag;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;

import java.util.Collection;
import java.util.Collections;

public class TranslationQueryTransformer implements QueryTransformer {

    private static final String TRANSLATION_PROMPT_TEMPLATE = """
            你是一位航空维修领域的翻译专家。
            请将以下用户问题翻译成英文，用于检索 FAA 维修手册。
            只返回翻译结果，不要解释，不要添加额外内容。

            用户问题：%s
            """;

    private final ChatModel chatModel;
    private final boolean enabled;

    public TranslationQueryTransformer(ChatModel chatModel, boolean enabled) {
        this.chatModel = chatModel;
        this.enabled = enabled;
    }

    @Override
    public Collection<Query> transform(Query query) {
        if (!enabled) {
            return Collections.singletonList(query);
        }

        String originalText = query.text();

        // 简单启发式：检测是否包含中文字符
        if (!containsChinese(originalText)) {
            return Collections.singletonList(query);
        }

        String prompt = String.format(TRANSLATION_PROMPT_TEMPLATE, originalText);
        String translatedText = chatModel.chat(prompt);

        // 清理可能的首尾空白和引号
        translatedText = translatedText.trim();
        if (translatedText.startsWith("\"") && translatedText.endsWith("\"")) {
            translatedText = translatedText.substring(1, translatedText.length() - 1);
        }

        return Collections.singletonList(Query.from(translatedText));
    }

    private boolean containsChinese(String text) {
        return text.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x4E00 && codePoint <= 0x9FFF) ||   // CJK Unified Ideographs
                        (codePoint >= 0x3400 && codePoint <= 0x4DBF) ||   // CJK Extension A
                        (codePoint >= 0xF900 && codePoint <= 0xFAFF)      // CJK Compatibility Ideographs
        );
    }
}
