package io.github.magcal90.comanche_operations_service.ingestion;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
final class KafkaEventValidator {

	static final String TELEMETRY_TOPIC = "comanche.device.telemetry.v1";
	static final String STATUS_TOPIC = "comanche.device.status.v1";
	static final String DEVICE_EVENTS_TOPIC = "comanche.device.events.v1";

	private static final Map<String, String> EVENT_TYPES = Map.of(
			TELEMETRY_TOPIC, "DEVICE_TELEMETRY_RECEIVED",
			STATUS_TOPIC, "DEVICE_STATUS_RECEIVED",
			DEVICE_EVENTS_TOPIC, "DEVICE_EVENT_RECEIVED");

	private final ObjectMapper objectMapper;

	KafkaEventValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	ValidatedDeviceEvent validate(String topic, String json) {
		String expectedType = EVENT_TYPES.get(topic);
		if (expectedType == null) throw new InvalidEventException("Unsupported source topic: " + topic);
		try {
			JsonNode root = objectMapper.readTree(json);
			if (!root.isObject()) throw new InvalidEventException("Event must be a JSON object");
			String eventId = requiredText(root, "eventId");
			String eventType = requiredText(root, "eventType");
			if (!expectedType.equals(eventType)) throw new InvalidEventException("Event type does not match source topic");
			String version = requiredText(root, "schemaVersion");
			if (!"1.0".equals(version)) throw new InvalidEventException("Unsupported schemaVersion: " + version);
			String deviceId = requiredText(root, "deviceId");
			String aggregateId = requiredText(root, "aggregateId");
			if (!deviceId.equals(aggregateId)) throw new InvalidEventException("aggregateId must equal deviceId");
			JsonNode payload = root.get("payload");
			if (payload == null || !payload.isObject()) throw new InvalidEventException("payload must be an object");
			validatePayload(topic, payload);
			return new ValidatedDeviceEvent(eventId, eventType, version,
					instant(root, "occurredAt"), instant(root, "publishedAt"),
					requiredText(root, "source"), aggregateId, requiredText(root, "correlationId"),
					requiredText(root, "sourceMessageId"), deviceId, payload);
		}
		catch (InvalidEventException ex) { throw ex; }
		catch (Exception ex) { throw new InvalidEventException("Invalid event JSON", ex); }
	}

	private static void validatePayload(String topic, JsonNode payload) {
		if (TELEMETRY_TOPIC.equals(topic)) {
			JsonNode metrics = payload.get("metrics");
			if (metrics == null || !metrics.isObject() || metrics.isEmpty())
				throw new InvalidEventException("Telemetry metrics must be a non-empty object");
			metrics.properties().forEach(entry -> {
				if (entry.getKey().isBlank() || !(entry.getValue().isNumber() || entry.getValue().isString()))
					throw new InvalidEventException("Metric values must be numeric or text");
			});
		}
		if (STATUS_TOPIC.equals(topic) && payload.isEmpty())
			throw new InvalidEventException("Status payload must not be empty");
		if (DEVICE_EVENTS_TOPIC.equals(topic)) {
			String type = requiredText(payload, "eventType");
			if ("OPERATIONAL_STATE_CHANGED".equals(type)) requiredText(payload, "newState");
		}
	}

	private static String requiredText(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isString() || value.asString().isBlank())
			throw new InvalidEventException("Missing or invalid " + field);
		return value.asString();
	}

	private static Instant instant(JsonNode root, String field) {
		try { return Instant.parse(requiredText(root, field)); }
		catch (DateTimeParseException ex) { throw new InvalidEventException("Invalid " + field, ex); }
	}
}
