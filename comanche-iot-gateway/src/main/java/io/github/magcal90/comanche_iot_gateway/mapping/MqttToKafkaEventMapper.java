package io.github.magcal90.comanche_iot_gateway.mapping;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

import io.github.magcal90.comanche_iot_gateway.contract.KafkaEventEnvelope;
import io.github.magcal90.comanche_iot_gateway.contract.MqttEnvelope;

import org.springframework.stereotype.Component;

@Component
public final class MqttToKafkaEventMapper {

	private static final String SOURCE = "comanche-iot-gateway";
	private final Clock clock;

	public MqttToKafkaEventMapper(Clock clock) {
		this.clock = clock;
	}

	public KafkaEventEnvelope map(MqttEnvelope source) {
		return new KafkaEventEnvelope(
				stableEventId(source),
				source.messageType().kafkaEventType(),
				source.schemaVersion(),
				source.eventTime(),
				clock.instant(),
				SOURCE,
				source.deviceId(),
				source.messageId(),
				source.messageId(),
				source.deviceId(),
				source.payload());
	}

	private static String stableEventId(MqttEnvelope source) {
		String logicalIdentity = SOURCE + ':' + source.deviceId() + ':' + source.messageId();
		return UUID.nameUUIDFromBytes(logicalIdentity.getBytes(StandardCharsets.UTF_8)).toString();
	}
}
