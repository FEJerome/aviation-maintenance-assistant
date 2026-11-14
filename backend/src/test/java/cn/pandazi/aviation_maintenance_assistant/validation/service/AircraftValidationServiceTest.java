package cn.pandazi.aviation_maintenance_assistant.validation.service;

import cn.pandazi.aviation_maintenance_assistant.validation.dto.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AircraftValidationServiceTest {

    private final AircraftValidationService service = new AircraftValidationService();

    @Test
    void validatesKnownCombinationsAfterNormalization() {
        assertThat(service.validate("Flight Design CTLS", "Rotax 912 ULS"))
                .isEqualTo(ValidationResult.MATCH);
    }

    @Test
    void distinguishesMismatchFromUnknown() {
        assertThat(service.validate("B737-800", "PW1100G"))
                .isEqualTo(ValidationResult.MISMATCH);
        assertThat(service.validate("UNKNOWN-2000", "ROTAX 912"))
                .isEqualTo(ValidationResult.UNKNOWN);
    }
}
