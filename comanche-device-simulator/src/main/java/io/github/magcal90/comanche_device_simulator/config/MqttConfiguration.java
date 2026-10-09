package io.github.magcal90.comanche_device_simulator.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record MqttConfiguration(URI serverUri, Duration connectionTimeout, Duration keepAliveInterval) {
    public MqttConfiguration {
        Objects.requireNonNull(serverUri, "serverUri");
        Objects.requireNonNull(connectionTimeout, "connectionTimeout");
        Objects.requireNonNull(keepAliveInterval, "keepAliveInterval");
        if (!"tcp".equalsIgnoreCase(serverUri.getScheme())) {
            throw new IllegalArgumentException("MQTT serverUri must use tcp for the MQTT 3.1.1 MVP");
        }
        if (connectionTimeout.isZero() || connectionTimeout.isNegative()) {
            throw new IllegalArgumentException("connectionTimeout must be positive");
        }
        if (keepAliveInterval.isZero() || keepAliveInterval.isNegative()) {
            throw new IllegalArgumentException("keepAliveInterval must be positive");
        }
    }
}
