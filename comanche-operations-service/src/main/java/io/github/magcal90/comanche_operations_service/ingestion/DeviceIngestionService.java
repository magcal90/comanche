package io.github.magcal90.comanche_operations_service.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceIngestionService {

	enum Result { PROCESSED, DUPLICATE }

	private final JdbcClient jdbc;
	private final KafkaEventValidator validator;
	private final ObjectMapper objectMapper;

	DeviceIngestionService(JdbcClient jdbc, KafkaEventValidator validator, ObjectMapper objectMapper) {
		this.jdbc = jdbc;
		this.validator = validator;
		this.objectMapper = objectMapper;
	}

	@Transactional
	Result process(String topic, String rawEvent, Instant receivedAt) {
		ValidatedDeviceEvent event = validator.validate(topic, rawEvent);
		if (!deviceExists(event.deviceId())) throw new UnknownDeviceException(event.deviceId());

		String hash = eventHash(event);
		int claimed = jdbc.sql("""
				INSERT INTO processed_event(event_id, device_id, source_message_id, event_type, source_topic, payload_hash, processed_at)
				VALUES (:eventId, :deviceId, :sourceMessageId, :eventType, :topic, :hash, :processedAt)
				ON CONFLICT DO NOTHING
				""")
				.param("eventId", event.eventId()).param("deviceId", event.deviceId())
				.param("sourceMessageId", event.sourceMessageId()).param("eventType", event.eventType())
				.param("topic", topic).param("hash", hash).param("processedAt", dbTime(receivedAt)).update();
		if (claimed == 0) return classifyDuplicate(event, topic, hash);

		ensureCurrentState(event.deviceId(), receivedAt);
		if (KafkaEventValidator.TELEMETRY_TOPIC.equals(topic)) persistTelemetry(event, receivedAt);
		else if (KafkaEventValidator.STATUS_TOPIC.equals(topic)) persistStatus(event, receivedAt);
		else persistDeviceEvent(event, receivedAt);
		return Result.PROCESSED;
	}

	private Result classifyDuplicate(ValidatedDeviceEvent event, String topic, String hash) {
		Map<String, Object> existing = jdbc.sql("""
				SELECT event_id, device_id, source_message_id, event_type, source_topic, payload_hash
				FROM processed_event
				WHERE event_id = :eventId
				   OR (device_id = :deviceId AND source_message_id = :sourceMessageId)
				""")
				.param("eventId", event.eventId()).param("deviceId", event.deviceId())
				.param("sourceMessageId", event.sourceMessageId()).param("eventType", event.eventType())
				.query().singleRow();
		boolean exact = event.eventId().equals(existing.get("event_id"))
				&& event.deviceId().equals(existing.get("device_id"))
				&& event.sourceMessageId().equals(existing.get("source_message_id"))
				&& event.eventType().equals(existing.get("event_type"))
				&& topic.equals(existing.get("source_topic"))
				&& hash.equals(existing.get("payload_hash"));
		if (exact) return Result.DUPLICATE;
		throw new EventIdentityConflictException("Event identity conflicts with an existing event");
	}

	private void persistTelemetry(ValidatedDeviceEvent event, Instant receivedAt) {
		JsonNode metrics = event.payload().get("metrics");
		jdbc.sql("""
				INSERT INTO device_telemetry(event_id, device_id, source_message_id, schema_version,
				 occurred_at, published_at, operations_received_at, correlation_id, metrics)
				VALUES (:eventId, :deviceId, :sourceMessageId, :schemaVersion,
				 :occurredAt, :publishedAt, :receivedAt, :correlationId, CAST(:metrics AS jsonb))
				""")
				.param("eventId", event.eventId()).param("deviceId", event.deviceId())
				.param("sourceMessageId", event.sourceMessageId()).param("schemaVersion", event.schemaVersion())
				.param("occurredAt", dbTime(event.occurredAt())).param("publishedAt", dbTime(event.publishedAt()))
				.param("receivedAt", dbTime(receivedAt)).param("correlationId", event.correlationId())
				.param("metrics", json(metrics)).update();

		metrics.properties().forEach(metric -> upsertMetric(event, metric.getKey(), metric.getValue(), receivedAt));
		updateLastSeen(event.deviceId(), event.occurredAt(), receivedAt);
	}

	private void upsertMetric(ValidatedDeviceEvent event, String name, JsonNode value, Instant receivedAt) {
		boolean numeric = value.isNumber();
		jdbc.sql("""
				INSERT INTO device_current_metric(device_id, metric_name, value_type, numeric_value, text_value,
				 observed_at, source_event_id, updated_at)
				VALUES (:deviceId, :name, :valueType, :numericValue, :textValue, :observedAt, :eventId, :updatedAt)
				ON CONFLICT (device_id, metric_name) DO UPDATE SET
				 value_type = EXCLUDED.value_type, numeric_value = EXCLUDED.numeric_value,
				 text_value = EXCLUDED.text_value, observed_at = EXCLUDED.observed_at,
				 source_event_id = EXCLUDED.source_event_id, updated_at = EXCLUDED.updated_at
				WHERE device_current_metric.observed_at < EXCLUDED.observed_at
				""")
				.param("deviceId", event.deviceId()).param("name", name)
				.param("valueType", numeric ? "NUMBER" : "TEXT")
				.param("numericValue", numeric ? value.decimalValue() : null, Types.NUMERIC)
				.param("textValue", numeric ? null : value.asString(), Types.VARCHAR)
				.param("observedAt", dbTime(event.occurredAt())).param("eventId", event.eventId())
				.param("updatedAt", dbTime(receivedAt)).update();
	}

	private void persistStatus(ValidatedDeviceEvent event, Instant receivedAt) {
		String payload = json(event.payload());
		jdbc.sql("""
				INSERT INTO device_status_observation(event_id, device_id, source_message_id, occurred_at,
				 published_at, operations_received_at, status_payload)
				VALUES (:eventId, :deviceId, :sourceMessageId, :occurredAt, :publishedAt, :receivedAt, CAST(:payload AS jsonb))
				""").param("eventId", event.eventId()).param("deviceId", event.deviceId())
				.param("sourceMessageId", event.sourceMessageId()).param("occurredAt", dbTime(event.occurredAt()))
				.param("publishedAt", dbTime(event.publishedAt())).param("receivedAt", dbTime(receivedAt))
				.param("payload", payload).update();
		jdbc.sql("""
				UPDATE device_current_state SET reported_status = CAST(:payload AS jsonb),
				 status_observed_at = :observedAt, last_status_event_id = :eventId, updated_at = :updatedAt,
				 version = version + 1
				WHERE device_id = :deviceId AND (status_observed_at IS NULL OR status_observed_at < :observedAt)
				""").param("payload", payload).param("observedAt", dbTime(event.occurredAt()))
				.param("eventId", event.eventId()).param("updatedAt", dbTime(receivedAt)).param("deviceId", event.deviceId()).update();
		updateLastSeen(event.deviceId(), event.occurredAt(), receivedAt);
	}

	private void persistDeviceEvent(ValidatedDeviceEvent event, Instant receivedAt) {
		String deviceEventType = event.payload().get("eventType").asString();
		jdbc.sql("""
				INSERT INTO device_event_observation(event_id, device_id, source_message_id, device_event_type,
				 occurred_at, published_at, operations_received_at, attributes)
				VALUES (:eventId, :deviceId, :sourceMessageId, :deviceEventType,
				 :occurredAt, :publishedAt, :receivedAt, CAST(:attributes AS jsonb))
				""").param("eventId", event.eventId()).param("deviceId", event.deviceId())
				.param("sourceMessageId", event.sourceMessageId()).param("deviceEventType", deviceEventType)
				.param("occurredAt", dbTime(event.occurredAt())).param("publishedAt", dbTime(event.publishedAt()))
				.param("receivedAt", dbTime(receivedAt)).param("attributes", json(event.payload())).update();
		if ("OPERATIONAL_STATE_CHANGED".equals(deviceEventType)) {
			String previous = event.payload().path("previousState").isString()
					? event.payload().path("previousState").asString() : null;
			String next = event.payload().get("newState").asString();
			jdbc.sql("""
					INSERT INTO device_operational_state_history(source_event_id, device_id, previous_state,
					 new_state, effective_at, operations_received_at)
					VALUES (:eventId, :deviceId, :previousState, :newState, :effectiveAt, :receivedAt)
					""").param("eventId", event.eventId()).param("deviceId", event.deviceId())
					.param("previousState", previous, Types.VARCHAR).param("newState", next)
					.param("effectiveAt", dbTime(event.occurredAt())).param("receivedAt", dbTime(receivedAt)).update();
			jdbc.sql("""
					UPDATE device_current_state SET operational_state = :newState,
					 operational_state_observed_at = :observedAt, last_operational_event_id = :eventId,
					 updated_at = :updatedAt, version = version + 1
					WHERE device_id = :deviceId
					 AND (operational_state_observed_at IS NULL OR operational_state_observed_at < :observedAt)
					""").param("newState", next).param("observedAt", dbTime(event.occurredAt()))
					.param("eventId", event.eventId()).param("updatedAt", dbTime(receivedAt))
					.param("deviceId", event.deviceId()).update();
		}
		updateLastSeen(event.deviceId(), event.occurredAt(), receivedAt);
	}

	private void ensureCurrentState(String deviceId, Instant receivedAt) {
		jdbc.sql("""
				INSERT INTO device_current_state(device_id, updated_at)
				VALUES (:deviceId, :updatedAt) ON CONFLICT (device_id) DO NOTHING
				""").param("deviceId", deviceId).param("updatedAt", dbTime(receivedAt)).update();
	}

	private void updateLastSeen(String deviceId, Instant occurredAt, Instant receivedAt) {
		jdbc.sql("""
				UPDATE device_current_state SET last_seen_at = GREATEST(COALESCE(last_seen_at, :occurredAt), :occurredAt),
				 updated_at = :updatedAt, version = version + 1 WHERE device_id = :deviceId
				""").param("occurredAt", dbTime(occurredAt)).param("updatedAt", dbTime(receivedAt)).param("deviceId", deviceId).update();
	}

	private boolean deviceExists(String deviceId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM device WHERE device_id = :deviceId)")
				.param("deviceId", deviceId).query(Boolean.class).single();
	}

	private static OffsetDateTime dbTime(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private String eventHash(ValidatedDeviceEvent event) {
		String canonical = String.join("\u001f", event.eventId(), event.eventType(), event.schemaVersion(),
				event.occurredAt().toString(), event.publishedAt().toString(), event.source(), event.aggregateId(),
				event.correlationId(), event.sourceMessageId(), event.deviceId(), json(event.payload()));
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
	}

	private String json(JsonNode node) {
		try { return objectMapper.writeValueAsString(node); }
		catch (JacksonException ex) { throw new InvalidEventException("Cannot serialize event payload", ex); }
	}
}
