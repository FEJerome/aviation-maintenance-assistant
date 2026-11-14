package cn.pandazi.aviation_maintenance_assistant.chat.routing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AircraftInfoExtractorTest {

    @Test
    void extractsOnlyExplicitValues() {
        var info = AircraftInfoExtractor.extract("B737-800 with CFM56-7B oil");

        assertThat(info.model()).isEqualTo("B737-800");
        assertThat(info.engine()).isEqualTo("CFM56-7B");
    }

    @Test
    void neverInfersAnEngineForSingleEngineAircraft() {
        for (String model : new String[]{"CTLS", "WT9"}) {
            var info = AircraftInfoExtractor.extract(model + " engine oil");

            assertThat(info.model()).isEqualTo(model);
            assertThat(info.engine()).isNull();
            assertThat(info.isComplete()).isFalse();
        }
    }
}
