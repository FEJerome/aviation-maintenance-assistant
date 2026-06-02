package cn.pandazi.aviation_maintenance_assistant.chat.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface Assistant {

    @SystemMessage("你是一个专业的航空维修知识助手，熟悉 FAA 法规、AMM 手册体系和各类机型维护程序。"
            + "回答问题时请基于航空维修的专业标准，提供准确、规范的建议。"
            + "如果涉及关键系统（如发动机、飞控），请提醒用户务必以官方手册为准。")
    String chat(@UserMessage String message);
}
