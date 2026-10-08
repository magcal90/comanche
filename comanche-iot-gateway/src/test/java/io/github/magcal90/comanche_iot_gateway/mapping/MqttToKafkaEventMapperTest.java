package io.github.magcal90.comanche_iot_gateway.mapping;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.github.magcal90.comanche_iot_gateway.contract.MqttEnvelope;
import io.github.magcal90.comanche_iot_gateway.mqtt.MqttMessageType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MqttToKafkaEventMapperTest {

	private static final Instant PUBLISHED_AT = Instant.parse("2026-10-08T04:00:01Z");
	private final MqttToKafkaEventMapper mapper = new MqttToKafkaEventMapper(
			Clock.fixed(PUBLISHED_AT, ZoneOffset.UTC));

	@Test
	void map_whenSameMqttMessageIsRedelivered_shouldPreserveLogicalEventIdentity() throws Exception {
		var payload = new ObjectMapper().readTree("{\"metrics\":{\"printer.temperature\":72.0}}");
		var mqtt = new MqttEnvelope("1.0", "tel-10001", "KIOSK-TAM-003",
				Instant.parse("2026-10-08T04:00:00Z"), MqttMessageType.TELEMETRY, payload);

		var first = mapper.map(mqtt);
		var duplicate = mapper.map(mqtt);

		assertThat(duplicate.eventId()).isEqualTo(first.eventId());
		assertThat(first.eventType()).isEqualTo("DEVICE_TELEMETRY_RECEIVED");
		assertThat(first.sourceMessageId()).isEqualTo("tel-10001");
		assertThat(first.occurredAt()).isEqualTo(mqtt.eventTime());
		assertThat(first.publishedAt()).isEqualTo(PUBLISHED_AT);
	}
}
