package io.github.magcal90.comanche_iot_gateway.contract;

import java.time.Instant;

import io.github.magcal90.comanche_iot_gateway.mqtt.MqttMessageType;
import tools.jackson.databind.JsonNode;

public record MqttEnvelope(
		String schemaVersion,
		String messageId,
		String deviceId,
		Instant eventTime,
		MqttMessageType messageType,
		JsonNode payload) {
}
