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
                fault-scenarios=[]
                """));

        assertThat(config.tickInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.kiosks()).extracting(KioskConfiguration::deviceId)
                .containsExactly("KIOSK-TEST-004", "KIOSK-TEST-005");
    }

    @Test
    void constructor_whenDeviceIdsAreDuplicated_shouldRejectConfiguration() {
        var kiosk = new KioskConfiguration("KIOSK-1", "SITE-1");

        assertThatThrownBy(() -> new SimulatorConfiguration(1, Instant.EPOCH, Duration.ofSeconds(1),
                1, 1, 10, 2, Duration.ofMillis(10), Duration.ofSeconds(1), mqtt(), List.of(kiosk, kiosk), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate deviceId");
    }

    @Test
    void constructor_whenTickIntervalIsNotPositive_shouldRejectConfiguration() {
        assertThatThrownBy(() -> new SimulatorConfiguration(1, Instant.EPOCH, Duration.ZERO,
                1, 1, 10, 2, Duration.ofMillis(10), Duration.ofSeconds(1), mqtt(),
                List.of(new KioskConfiguration("KIOSK-1", "SITE-1")), List.of()))
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

    @Test
    void from_whenFaultScenarioTypeIsUnknown_shouldRejectConfiguration() {
        assertThatThrownBy(() -> SimulatorConfigurationLoader.from(ConfigFactory.parseString(baseConfig("""
                fault-scenarios=[{
                  scenario-id="BAD-1", device-id="KIOSK-TEST-004", fault-type="DISK_FIRE",
                  start-after-ticks=1, step-per-tick=1, maximum=10,
                  recovery-step-per-tick=1, disabling-threshold=9
                }]
                """))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid fault-type");
    }

    @Test
    void from_whenFaultScenarioTargetsUnknownKiosk_shouldRejectConfiguration() {
        assertThatThrownBy(() -> SimulatorConfigurationLoader.from(ConfigFactory.parseString(baseConfig("""
                fault-scenarios=[{
                  scenario-id="CPU-1", device-id="UNKNOWN", fault-type="CPU_PRESSURE",
                  start-after-ticks=1, step-per-tick=5, maximum=98,
                  recovery-step-per-tick=10, disabling-threshold=95
                }]
                """))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown deviceId");
    }

    @Test
    void constructor_whenFaultProgressionOrStartIsInvalid_shouldRejectConfiguration() {
        assertThatThrownBy(() -> new FaultScenarioConfiguration("CPU-1", "KIOSK-1",
                io.github.magcal90.comanche_device_simulator.model.FaultType.CPU_PRESSURE,
                -1, 0, 101, 1, 95, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startAfterTicks");

        assertThatThrownBy(() -> new FaultScenarioConfiguration("MEMORY-1", "KIOSK-1",
                io.github.magcal90.comanche_device_simulator.model.FaultType.MEMORY_PRESSURE,
                1, 0, 95, 1, 90, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primaryStep");

        assertThatThrownBy(() -> new FaultScenarioConfiguration("CPU-2", "KIOSK-1",
                io.github.magcal90.comanche_device_simulator.model.FaultType.CPU_PRESSURE,
                1, 5, 90, 1, 95, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disablingThreshold");
    }

    private static String baseConfig(String scenarios) {
        return """
                random-seed=42
                start-time="2026-10-08T00:00:00Z"
                tick-interval=1s
                telemetry-interval-ticks=1
                status-interval-ticks=1
                kiosk-count=1
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
                """ + scenarios;
    }

    private static MqttConfiguration mqtt() {
        return new MqttConfiguration(URI.create("tcp://localhost:1883"), Duration.ofSeconds(2), Duration.ofSeconds(10));
    }
}
