package io.github.magcal90.comanche_device_simulator.config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatorConfigurationTest {
    @Test
    void from_whenConfigurationIsValid_shouldGenerateConfiguredKiosks() {
        var config = SimulatorConfigurationLoader.from(ConfigFactory.parseString("""
                random-seed=42
                start-time="2026-10-08T00:00:00Z"
                tick-interval=2s
                telemetry-interval-ticks=3
                status-interval-ticks=7
                kiosk-count=2
                device-id-prefix="KIOSK-TEST-"
                first-device-number=4
                site-id="SITE-TEST"
                publisher-buffer-capacity=10
                publication-max-attempts=3
                """));

        assertThat(config.tickInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.kiosks()).extracting(KioskConfiguration::deviceId)
                .containsExactly("KIOSK-TEST-004", "KIOSK-TEST-005");
    }

    @Test
    void constructor_whenDeviceIdsAreDuplicated_shouldRejectConfiguration() {
        var kiosk = new KioskConfiguration("KIOSK-1", "SITE-1");

        assertThatThrownBy(() -> new SimulatorConfiguration(1, Instant.EPOCH, Duration.ofSeconds(1),
                1, 1, 10, 2, List.of(kiosk, kiosk)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate deviceId");
    }

    @Test
    void constructor_whenTickIntervalIsNotPositive_shouldRejectConfiguration() {
        assertThatThrownBy(() -> new SimulatorConfiguration(1, Instant.EPOCH, Duration.ZERO,
                1, 1, 10, 2, List.of(new KioskConfiguration("KIOSK-1", "SITE-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tickInterval");
    }
}
