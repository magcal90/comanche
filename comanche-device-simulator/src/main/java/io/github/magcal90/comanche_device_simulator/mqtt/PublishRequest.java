package io.github.magcal90.comanche_device_simulator.mqtt;

import java.time.Instant;
import java.util.Map;

public record PublishRequest(
        String messageId,
        String deviceId,
        Instant eventTime,
        MessageCategory category,
        Map<String, Object> payload,
        int attempt) {
    public PublishRequest {
        payload = Map.copyOf(payload);
    }

    public PublishRequest nextAttempt() {
        return new PublishRequest(messageId, deviceId, eventTime, category, payload, attempt + 1);
    }
}
