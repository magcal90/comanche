package io.github.magcal90.comanche_operations_service.ingestion;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaEventValidatorTest {

	private final KafkaEventValidator validator = new KafkaEventValidator(new ObjectMapper());

	@Test
	void validatesTelemetryContract() {
		ValidatedDeviceEvent event = validator.validate(KafkaEventValidator.TELEMETRY_TOPIC,
				event("DEVICE_TELEMETRY_RECEIVED", "{\"metrics\":{\"temperature\":72.5}}"));

		assertThat(event.eventId()).isEqualTo("event-1");
		assertThat(event.payload().get("metrics").get("temperature").decimalValue()).isEqualByComparingTo("72.5");
	}

	@Test
	void rejectsTopicAndEventTypeMismatch() {
		assertThatThrownBy(() -> validator.validate(KafkaEventValidator.STATUS_TOPIC,
				event("DEVICE_TELEMETRY_RECEIVED", "{}")))
				.isInstanceOf(InvalidEventException.class)
				.hasMessageContaining("does not match");
	}

	@Test
	void rejectsUnsupportedVersionAndMalformedPayloads() {
		assertThatThrownBy(() -> validator.validate(KafkaEventValidator.TELEMETRY_TOPIC,
				event("DEVICE_TELEMETRY_RECEIVED", "{\"metrics\":{\"temperature\":true}}")))
				.isInstanceOf(InvalidEventException.class);
		assertThatThrownBy(() -> validator.validate(KafkaEventValidator.TELEMETRY_TOPIC,
				event("DEVICE_TELEMETRY_RECEIVED", "{\"metrics\":{}}").replace("\"1.0\"", "\"2.0\"")))
				.isInstanceOf(InvalidEventException.class)
				.hasMessageContaining("schemaVersion");
	}

	private static String event(String type, String payload) {
		return """
				{"eventId":"event-1","eventType":"%s","schemaVersion":"1.0",
				 "occurredAt":"2026-10-08T04:00:00Z","publishedAt":"2026-10-08T04:00:01Z",
				 "source":"comanche-iot-gateway","aggregateId":"device-1","correlationId":"message-1",
				 "sourceMessageId":"message-1","deviceId":"device-1","payload":%s}
				""".formatted(type, payload);
	}
}
