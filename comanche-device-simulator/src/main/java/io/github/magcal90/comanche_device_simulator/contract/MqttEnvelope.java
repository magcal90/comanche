package io.github.magcal90.comanche_device_simulator.contract;

import java.util.Map;

public record MqttEnvelope(
        String schemaVersion,
        String messageId,
        String deviceId,
        String eventTime,
        String messageType,
        Map<String, Object> payload) {}
