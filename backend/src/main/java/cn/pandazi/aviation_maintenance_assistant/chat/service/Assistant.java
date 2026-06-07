package cn.pandazi.aviation_maintenance_assistant.chat.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface Assistant {

    @SystemMessage("你是一个专业的航空维修知识助手，熟悉 FAA 法规、AMM 手册体系和各类机型维护程序。"
            + "回答问题时，你会收到与问题相关的维修手册片段，请严格基于这些片段回答。"
            + "如果片段中未包含答案，请明确说明\"未在手册中找到相关内容\"。"
            + "如果涉及关键系统（如发动机、飞控），请提醒用户务必以官方手册为准。"
            + "回答时请标注引用来源（如手册章节号），保持专业、规范的语气。")
    String chat(@UserMessage String message);

    @SystemMessage("你是一个专业的航空维修知识助手，熟悉 FAA 法规、AMM 手册体系和各类机型维护程序。"
            + "回答问题时，你会收到与问题相关的维修手册片段，请严格基于这些片段回答。"
            + "如果片段中未包含答案，请明确说明\"未在手册中找到相关内容\"。"
            + "如果涉及关键系统（如发动机、飞控），请提醒用户务必以官方手册为准。"
            + "回答时请标注引用来源（如手册章节号），保持专业、规范的语气。")
    TokenStream chatStream(@UserMessage String message);
}
