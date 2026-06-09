package cn.pandazi.aviation_maintenance_assistant.chat.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link Assistant#SYSTEM_PROMPT} 包含 Markdown 格式化规则。
 * <p>
 * 该测试不调用真实 LLM，仅做静态断言，确保 prompt 不会被意外修改或丢失。
 */
class AssistantSystemPromptTest {

    @Test
    void systemPromptShouldForbidHeadings() {
        assertThat(Assistant.SYSTEM_PROMPT)
                .contains("不要使用 #、##、### 作为标题");
    }

    @Test
    void systemPromptShouldRequireParagraphSeparation() {
        assertThat(Assistant.SYSTEM_PROMPT)
                .contains("段落之间用一个空行分隔");
    }

    @Test
    void systemPromptShouldRequireListItemsOnOwnLine() {
        assertThat(Assistant.SYSTEM_PROMPT)
                .contains("每个列表项必须独占一行")
                .contains("禁止写成：1. 打开盖子 2. 检查油量");
    }

    @Test
    void systemPromptShouldContainBoldRule() {
        assertThat(Assistant.SYSTEM_PROMPT)
                .contains("**关键词**")
                .contains("禁止嵌套")
                .contains("禁止用加粗包裹整个段落");
    }

    @Test
    void systemPromptShouldContainCodeFenceRule() {
        assertThat(Assistant.SYSTEM_PROMPT)
                .contains("``` 独占一行写语言标识");
    }

    @Test
    void systemPromptShouldRequireEachBlockOnOwnLine() {
        assertThat(Assistant.SYSTEM_PROMPT)
                .contains("每个段落、每个列表项、每个代码块都必须独占一行或多行");
    }

    @Test
    void systemPromptShouldContainCitationAndSafetyRules() {
        assertThat(Assistant.SYSTEM_PROMPT)
                .contains("未在手册中找到相关内容")
                .contains("官方手册为准")
                .contains("标注引用来源");
    }
}
