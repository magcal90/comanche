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
        List<KioskConfiguration> kiosks) {

    public SimulatorConfiguration {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(tickInterval, "tickInterval");
        kiosks = List.copyOf(Objects.requireNonNull(kiosks, "kiosks"));
        if (tickInterval.isZero() || tickInterval.isNegative()) {
            throw new IllegalArgumentException("tickInterval must be positive");
        }
        requirePositive(telemetryIntervalTicks, "telemetryIntervalTicks");
        requirePositive(statusIntervalTicks, "statusIntervalTicks");
        requirePositive(publisherBufferCapacity, "publisherBufferCapacity");
        requirePositive(publicationMaxAttempts, "publicationMaxAttempts");
        if (kiosks.isEmpty()) throw new IllegalArgumentException("at least one kiosk is required");
        var ids = new HashSet<String>();
        kiosks.forEach(kiosk -> {
            if (!ids.add(kiosk.deviceId())) throw new IllegalArgumentException("duplicate deviceId: " + kiosk.deviceId());
        });
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
