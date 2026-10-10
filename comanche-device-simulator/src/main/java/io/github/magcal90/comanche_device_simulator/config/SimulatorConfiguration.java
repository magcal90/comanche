package io.github.magcal90.comanche_device_simulator.config;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SimulatorConfiguration(
        long randomSeed,
        Instant startTime,
        Duration tickInterval,
        int telemetryIntervalTicks,
        int statusIntervalTicks,
        int publisherBufferCapacity,
        int publicationMaxAttempts,
        Duration publicationInitialBackoff,
        Duration publicationMaxBackoff,
        MqttConfiguration mqtt,
        List<KioskConfiguration> kiosks,
        List<FaultScenarioConfiguration> faultScenarios) {

    public SimulatorConfiguration {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(tickInterval, "tickInterval");
        Objects.requireNonNull(publicationInitialBackoff, "publicationInitialBackoff");
        Objects.requireNonNull(publicationMaxBackoff, "publicationMaxBackoff");
        Objects.requireNonNull(mqtt, "mqtt");
        kiosks = List.copyOf(Objects.requireNonNull(kiosks, "kiosks"));
        faultScenarios = List.copyOf(Objects.requireNonNull(faultScenarios, "faultScenarios"));
        if (tickInterval.isZero() || tickInterval.isNegative()) {
            throw new IllegalArgumentException("tickInterval must be positive");
        }
        requirePositive(telemetryIntervalTicks, "telemetryIntervalTicks");
        requirePositive(statusIntervalTicks, "statusIntervalTicks");
        requirePositive(publisherBufferCapacity, "publisherBufferCapacity");
        requirePositive(publicationMaxAttempts, "publicationMaxAttempts");
        if (publicationInitialBackoff.isNegative() || publicationInitialBackoff.isZero()) {
            throw new IllegalArgumentException("publicationInitialBackoff must be positive");
        }
        if (publicationMaxBackoff.compareTo(publicationInitialBackoff) < 0) {
            throw new IllegalArgumentException("publicationMaxBackoff must be at least publicationInitialBackoff");
        }
        if (kiosks.isEmpty()) throw new IllegalArgumentException("at least one kiosk is required");
        var ids = new HashSet<String>();
        kiosks.forEach(kiosk -> {
            if (!ids.add(kiosk.deviceId())) throw new IllegalArgumentException("duplicate deviceId: " + kiosk.deviceId());
        });
        var scenarioIds = new HashSet<String>();
        var deviceFaults = new HashSet<String>();
        faultScenarios.forEach(scenario -> {
            if (!scenarioIds.add(scenario.scenarioId())) {
                throw new IllegalArgumentException("duplicate scenarioId: " + scenario.scenarioId());
            }
            if (!ids.contains(scenario.deviceId())) {
                throw new IllegalArgumentException("fault scenario targets unknown deviceId: " + scenario.deviceId());
            }
            if (!deviceFaults.add(scenario.deviceId() + ':' + scenario.faultType())) {
                throw new IllegalArgumentException("duplicate fault type for device: " + scenario.deviceId()
                        + " " + scenario.faultType());
            }
        });
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
