package cn.pandazi.aviation_maintenance_assistant.chat.routing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicClassifierTest {

    @Test
    void routesCriticalSystemsDeterministically() {
        assertThat(TopicClassifier.needsAircraftInfo("engine oil pressure")).isTrue();
        assertThat(TopicClassifier.needsAircraftInfo("起落架检查")).isTrue();
    }

    @Test
    void leavesGeneralQuestionsWithoutAircraftRequirements() {
        assertThat(TopicClassifier.needsAircraftInfo("What does AMM mean?")).isFalse();
    }
}
