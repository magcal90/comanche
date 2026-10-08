package io.github.magcal90.comanche_iot_gateway.validation;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import io.github.magcal90.comanche_iot_gateway.config.GatewayIngestionProperties;
import io.github.magcal90.comanche_iot_gateway.mqtt.MqttMessageType;
import io.github.magcal90.comanche_iot_gateway.mqtt.MqttTopic;
import io.github.magcal90.comanche_iot_gateway.port.DeviceRegistryPort;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqttEnvelopeValidatorTest {

	private static final String DEVICE_ID = "KIOSK-TAM-003";
	private final DeviceRegistryPort registry = DEVICE_ID::equals;
	private final MqttEnvelopeValidator validator = new MqttEnvelopeValidator(
			new ObjectMapper(),
			new GatewayIngestionProperties(Set.of(DEVICE_ID), DataSize.ofKilobytes(256), Duration.ofSeconds(2)),
			registry);

	@Test
	void validate_whenTelemetryIsValid_shouldPreserveIdentityAndEventTime() {
		var envelope = validator.validate(topic(DEVICE_ID), bytes(validTelemetry(DEVICE_ID, "msg-1")));

		assertThat(envelope.deviceId()).isEqualTo(DEVICE_ID);
		assertThat(envelope.messageId()).isEqualTo("msg-1");
		assertThat(envelope.eventTime()).hasToString("2026-10-08T04:00:00Z");
	}

	@Test
	void validate_whenTopicAndPayloadIdentityDiffer_shouldReject() {
		assertRejected(validTelemetry("KIOSK-TAM-999", "msg-1"), RejectionReason.TOPIC_IDENTITY_MISMATCH);
	}

	@Test
	void validate_whenDeviceIsUnknown_shouldReject() {
		assertThatThrownBy(() -> validator.validate(topic("UNKNOWN-1"), bytes(validTelemetry("UNKNOWN-1", "msg-1"))))
				.isInstanceOfSatisfying(InvalidDeviceMessageException.class,
						exception -> assertThat(exception.reason()).isEqualTo(RejectionReason.UNKNOWN_DEVICE));
	}

	@Test
	void validate_whenMetricRangeIsInvalid_shouldReject() {
		String payload = validTelemetry(DEVICE_ID, "msg-1").replace("0.19", "1.19");
		assertRejected(payload, RejectionReason.INVALID_METRIC_VALUE);
	}

	@Test
	void validate_whenJsonIsMalformed_shouldReject() {
		assertRejected("{not-json", RejectionReason.MALFORMED_JSON);
	}

	private void assertRejected(String payload, RejectionReason reason) {
		assertThatThrownBy(() -> validator.validate(topic(DEVICE_ID), bytes(payload)))
				.isInstanceOfSatisfying(InvalidDeviceMessageException.class,
						exception -> assertThat(exception.reason()).isEqualTo(reason));
	}

	private static MqttTopic topic(String deviceId) {
		return new MqttTopic(deviceId, MqttMessageType.TELEMETRY,
				"comanche/devices/" + deviceId + "/telemetry");
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static String validTelemetry(String deviceId, String messageId) {
		return """
				{
				  "schemaVersion":"1.0",
				  "messageId":"%s",
				  "deviceId":"%s",
				  "eventTime":"2026-10-08T04:00:00Z",
				  "messageType":"TELEMETRY",
				  "payload":{"metrics":{"printer.temperature":72.0,"printer.errorRate":0.19}}
				}
				""".formatted(messageId, deviceId);
	}
}
