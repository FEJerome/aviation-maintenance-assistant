package cn.pandazi.aviation_maintenance_assistant.chat.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AviationPromptTest {

    @Test
    void promptRequiresGroundedAnswersAndStableSourceMarkers() {
        assertThat(AviationPrompt.SYSTEM_PROMPT)
                .contains("supplied manual excerpts")
                .contains("[S1]")
                .contains("Never invent page numbers, chapter numbers")
                .contains("not found");
    }

    @Test
    void promptRetainsMarkdownAndMaintenanceSafetyRules() {
        assertThat(AviationPrompt.SYSTEM_PROMPT)
                .contains("Do not use Markdown headings")
                .contains("every list item on its own line")
                .contains("official current maintenance manual")
                .contains("not authorization to perform maintenance");
    }
}
