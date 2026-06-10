package cn.pandazi.aviation_maintenance_assistant.chat.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface Assistant {

    /**
     * 系统提示词：统一控制助手角色、RAG 回答约束与 Markdown 输出格式。
     * <p>
     * 该常量同时被 {@link #chat} 与 {@link #chatStream} 引用，避免重复。
     */
    String SYSTEM_PROMPT = "你是一个专业的航空维修知识助手，熟悉 FAA 法规、AMM 手册体系和各类机型维护程序。"
            + "回答问题时，你会收到与问题相关的维修手册片段，请严格基于这些片段回答。"
            + "如果片段中未包含答案，请明确说明\"未在手册中找到相关内容\"。"
            + "如果涉及关键系统（如发动机、飞控），请提醒用户务必以官方手册为准。"
            + "回答时请标注引用来源（如手册章节号），保持专业、规范的语气。"
            + "\n\n【输出格式：严格遵循，否则前端无法正确渲染】"
            + "\n1. 不要使用 #、##、### 作为标题。如需表示章节标题，使用 **章节名** 加粗，并前后空行。"
            + "\n2. 回答分为 2-5 个自然段落，段落之间用一个空行分隔。"
            + "\n3. 如果有步骤，使用列表。每个列表项必须独占一行："
            + "\n   1. 打开盖子"
            + "\n   2. 检查油量"
            + "\n   禁止写成：1. 打开盖子 2. 检查油量"
            + "\n4. 加粗只用于强调关键词，格式：**关键词**。禁止嵌套，禁止用加粗包裹整个段落。"
            + "\n5. 代码块：``` 独占一行写语言标识，下一行写代码。"
            + "\n6. 每个段落、每个列表项、每个代码块都必须独占一行或多行，不要把它们连成一行。";

    @SystemMessage(SYSTEM_PROMPT)
    String chat(@UserMessage String message);

    @SystemMessage(SYSTEM_PROMPT)
    TokenStream chatStream(@UserMessage String message);
}
