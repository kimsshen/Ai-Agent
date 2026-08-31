package com.example.industrialai.iiot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IiotDemoRepositoryTest {

    private final IiotDemoRepository repository = new IiotDemoRepository();

    @Test
    void dev001ShouldExposeTemperatureAndVibrationAlarms() {
        var snapshot = repository.snapshot("DEV-001");

        assertThat(snapshot.device().status().name()).isEqualTo("WARNING");
        assertThat(snapshot.telemetry().temperatureCelsius()).isGreaterThan(85.0);
        assertThat(snapshot.activeAlarms()).extracting("code")
                .containsExactlyInAnyOrder("MTR-TEMP-HIGH", "MTR-VIB-HIGH");
    }
}
