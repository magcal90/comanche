package io.github.magcal90.comanche_iot_gateway.mqtt;

import java.util.Arrays;

import io.github.magcal90.comanche_iot_gateway.validation.InvalidDeviceMessageException;
import io.github.magcal90.comanche_iot_gateway.validation.RejectionReason;

import org.springframework.stereotype.Component;

@Component
public final class MqttTopicParser {

	private static final String PREFIX = "comanche/devices/";

	public MqttTopic parse(String topic) {
		if (topic == null || !topic.startsWith(PREFIX)) {
			throw new InvalidDeviceMessageException(RejectionReason.INVALID_TOPIC, "Topic is outside the device namespace");
		}
		String[] segments = topic.substring(PREFIX.length()).split("/", -1);
		if (segments.length != 2 || segments[0].isBlank() || segments[1].isBlank()) {
			throw new InvalidDeviceMessageException(RejectionReason.INVALID_TOPIC, "Topic must identify one device and category");
		}
		MqttMessageType type = Arrays.stream(MqttMessageType.values())
				.filter(candidate -> candidate.topicSuffix().equals(segments[1]))
				.findFirst()
				.orElseThrow(() -> new InvalidDeviceMessageException(
						RejectionReason.INVALID_TOPIC, "Unsupported device topic category"));
		return new MqttTopic(segments[0], type, topic);
	}
}
