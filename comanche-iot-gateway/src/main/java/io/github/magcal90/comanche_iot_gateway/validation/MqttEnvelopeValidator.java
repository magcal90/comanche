package io.github.magcal90.comanche_iot_gateway.validation;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

import io.github.magcal90.comanche_iot_gateway.config.GatewayIngestionProperties;
import io.github.magcal90.comanche_iot_gateway.contract.MqttEnvelope;
import io.github.magcal90.comanche_iot_gateway.mqtt.MqttMessageType;
import io.github.magcal90.comanche_iot_gateway.mqtt.MqttTopic;
import io.github.magcal90.comanche_iot_gateway.port.DeviceRegistryPort;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public final class MqttEnvelopeValidator {

	private static final String SUPPORTED_SCHEMA_VERSION = "1.0";
	private static final Set<String> REQUIRED_FIELDS = Set.of(
			"schemaVersion", "messageId", "deviceId", "eventTime", "messageType", "payload");
	private static final Map<String, Range> NUMERIC_METRICS = Map.of(
			"system.cpu.utilization", new Range(0, 100),
			"system.memory.utilization", new Range(0, 100),
			"printer.temperature", Range.unbounded(),
			"printer.errorRate", new Range(0, 1),
			"printer.paperLevel", new Range(0, 100));
	private static final Set<String> ENUM_METRICS = Set.of(
			"network.status", "application.status", "printer.status", "touchscreen.status",
			"paymentTerminal.status");

	private final ObjectMapper objectMapper;
	private final GatewayIngestionProperties properties;
	private final DeviceRegistryPort deviceRegistry;

	public MqttEnvelopeValidator(
			ObjectMapper objectMapper,
			GatewayIngestionProperties properties,
			DeviceRegistryPort deviceRegistry) {
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.deviceRegistry = deviceRegistry;
	}

	public MqttEnvelope validate(MqttTopic topic, byte[] payload) {
		if (payload.length > properties.maxMessageSize().toBytes()) {
			throw new InvalidDeviceMessageException(RejectionReason.PAYLOAD_TOO_LARGE, "MQTT payload exceeds configured limit");
		}
		JsonNode root = parse(payload);
		if (!root.isObject()) {
			throw new InvalidDeviceMessageException(RejectionReason.MALFORMED_JSON, "MQTT envelope must be a JSON object");
		}
		REQUIRED_FIELDS.forEach(field -> requireField(root, field));

		String schemaVersion = text(root, "schemaVersion");
		if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
			throw new InvalidDeviceMessageException(RejectionReason.UNSUPPORTED_SCHEMA_VERSION, "Unsupported schema version");
		}
		String messageId = text(root, "messageId");
		String deviceId = text(root, "deviceId");
		if (!topic.deviceId().equals(deviceId)) {
			throw new InvalidDeviceMessageException(RejectionReason.TOPIC_IDENTITY_MISMATCH, "Topic and payload device identities differ");
		}
		if (!deviceRegistry.isRegistered(deviceId)) {
			throw new InvalidDeviceMessageException(RejectionReason.UNKNOWN_DEVICE, "Device is not registered");
		}
		MqttMessageType messageType = parseMessageType(text(root, "messageType"));
		if (topic.messageType() != messageType) {
			throw new InvalidDeviceMessageException(RejectionReason.TOPIC_TYPE_MISMATCH, "Topic category and message type differ");
		}
		Instant eventTime = parseTimestamp(text(root, "eventTime"));
		JsonNode messagePayload = root.get("payload");
		if (!messagePayload.isObject()) {
			throw new InvalidDeviceMessageException(RejectionReason.INVALID_PAYLOAD, "Message payload must be an object");
		}
		validatePayload(messageType, messagePayload);
		return new MqttEnvelope(schemaVersion, messageId, deviceId, eventTime, messageType, messagePayload);
	}

	private JsonNode parse(byte[] payload) {
		try {
			return objectMapper.readTree(payload);
		}
		catch (JacksonException ex) {
			throw new InvalidDeviceMessageException(RejectionReason.MALFORMED_JSON, "Malformed MQTT JSON", ex);
		}
	}

	private void validatePayload(MqttMessageType type, JsonNode payload) {
		switch (type) {
			case TELEMETRY -> validateMetrics(payload);
			case STATUS -> requireNonEmptyObject(payload, "status payload");
			case DEVICE_EVENT -> text(payload, "eventType");
		}
	}

	private void validateMetrics(JsonNode payload) {
		JsonNode metrics = payload.get("metrics");
		if (metrics == null || !metrics.isObject() || metrics.isEmpty()) {
			throw new InvalidDeviceMessageException(RejectionReason.INVALID_PAYLOAD, "Telemetry metrics must be a non-empty object");
		}
		metrics.properties().forEach(entry -> validateMetric(entry.getKey(), entry.getValue()));
	}

	private void validateMetric(String name, JsonNode value) {
		Range range = NUMERIC_METRICS.get(name);
		if (range != null) {
			if (!value.isNumber() || !range.contains(value.doubleValue())) {
				throw new InvalidDeviceMessageException(RejectionReason.INVALID_METRIC_VALUE, "Invalid value for metric " + name);
			}
			return;
		}
		if (ENUM_METRICS.contains(name)) {
			if (!value.isString() || value.stringValue().isBlank()) {
				throw new InvalidDeviceMessageException(RejectionReason.INVALID_METRIC_VALUE, "Invalid value for metric " + name);
			}
			return;
		}
		throw new InvalidDeviceMessageException(RejectionReason.UNSUPPORTED_METRIC, "Unsupported metric " + name);
	}

	private static void requireNonEmptyObject(JsonNode payload, String description) {
		if (payload.isEmpty()) {
			throw new InvalidDeviceMessageException(RejectionReason.INVALID_PAYLOAD, description + " must not be empty");
		}
	}

	private static void requireField(JsonNode root, String field) {
		if (!root.hasNonNull(field)) {
			throw new InvalidDeviceMessageException(RejectionReason.MISSING_REQUIRED_FIELD, "Missing required field " + field);
		}
	}

	private static String text(JsonNode root, String field) {
		JsonNode value = root.get(field);
		if (value == null || !value.isString() || value.stringValue().isBlank()) {
			throw new InvalidDeviceMessageException(RejectionReason.MISSING_REQUIRED_FIELD, field + " must be a non-blank string");
		}
		return value.stringValue();
	}

	private static MqttMessageType parseMessageType(String value) {
		try {
			return MqttMessageType.valueOf(value);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidDeviceMessageException(RejectionReason.UNSUPPORTED_MESSAGE_TYPE, "Unsupported message type", ex);
		}
	}

	private static Instant parseTimestamp(String value) {
		try {
			return Instant.parse(value);
		}
		catch (DateTimeParseException ex) {
			throw new InvalidDeviceMessageException(RejectionReason.INVALID_TIMESTAMP, "eventTime must be ISO-8601", ex);
		}
	}

	private record Range(double minimum, double maximum) {
		static Range unbounded() {
			return new Range(-Double.MAX_VALUE, Double.MAX_VALUE);
		}

		boolean contains(double value) {
			return Double.isFinite(value) && value >= minimum && value <= maximum;
		}
	}
}
