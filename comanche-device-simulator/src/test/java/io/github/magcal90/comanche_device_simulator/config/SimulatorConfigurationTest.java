package io.github.magcal90.comanche_device_simulator.config;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
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
                publication-initial-backoff=10ms
                publication-max-backoff=1s
                mqtt.server-uri="tcp://localhost:1883"
                mqtt.connection-timeout=2s
                mqtt.keep-alive-interval=10s
                """));

        assertThat(config.tickInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.kiosks()).extracting(KioskConfiguration::deviceId)
                .containsExactly("KIOSK-TEST-004", "KIOSK-TEST-005");
    }

    @Test
    void constructor_whenDeviceIdsAreDuplicated_shouldRejectConfiguration() {
        var kiosk = new KioskConfiguration("KIOSK-1", "SITE-1");

        assertThatThrownBy(() -> new SimulatorConfiguration(1, Instant.EPOCH, Duration.ofSeconds(1),
                1, 1, 10, 2, Duration.ofMillis(10), Duration.ofSeconds(1), mqtt(), List.of(kiosk, kiosk)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate deviceId");
    }

    @Test
    void constructor_whenTickIntervalIsNotPositive_shouldRejectConfiguration() {
        assertThatThrownBy(() -> new SimulatorConfiguration(1, Instant.EPOCH, Duration.ZERO,
                1, 1, 10, 2, Duration.ofMillis(10), Duration.ofSeconds(1), mqtt(),
                List.of(new KioskConfiguration("KIOSK-1", "SITE-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tickInterval");
    }

    @Test
    void credentials_whenLoadedFromEnvironment_shouldRequireAndRedactValues() {
        MqttCredentials credentials = MqttCredentials.from(java.util.Map.of(
                "COMANCHE_SIMULATOR_MQTT_USERNAME", "device-user",
                "COMANCHE_SIMULATOR_MQTT_PASSWORD", "secret-value"));

        assertThat(credentials.username()).isEqualTo("device-user");
        assertThat(credentials.password()).containsExactly("secret-value".toCharArray());
        assertThat(credentials.toString()).doesNotContain("secret-value");
    }

    private static MqttConfiguration mqtt() {
        return new MqttConfiguration(URI.create("tcp://localhost:1883"), Duration.ofSeconds(2), Duration.ofSeconds(10));
    }
}
