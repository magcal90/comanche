package io.github.magcal90.comanche_iot_gateway.mqtt;

import io.github.magcal90.comanche_iot_gateway.validation.InvalidDeviceMessageException;
import io.github.magcal90.comanche_iot_gateway.validation.RejectionReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqttTopicParserTest {

	private final MqttTopicParser parser = new MqttTopicParser();

	@Test
	void parse_whenTelemetryTopicIsCanonical_shouldReturnDeviceAndType() {
		MqttTopic topic = parser.parse("comanche/devices/KIOSK-TAM-003/telemetry");

		assertThat(topic.deviceId()).isEqualTo("KIOSK-TAM-003");
		assertThat(topic.messageType()).isEqualTo(MqttMessageType.TELEMETRY);
	}

	@Test
	void parse_whenTopicHasAdditionalSegments_shouldReject() {
		assertThatThrownBy(() -> parser.parse("comanche/devices/KIOSK-TAM-003/telemetry/extra"))
				.isInstanceOfSatisfying(InvalidDeviceMessageException.class,
						exception -> assertThat(exception.reason()).isEqualTo(RejectionReason.INVALID_TOPIC));
	}
}
