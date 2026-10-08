package io.github.magcal90.comanche_iot_gateway.mqtt;

public enum MqttMessageType {
	TELEMETRY("telemetry", "DEVICE_TELEMETRY_RECEIVED", "comanche.device.telemetry.v1"),
	STATUS("status", "DEVICE_STATUS_RECEIVED", "comanche.device.status.v1"),
	DEVICE_EVENT("events", "DEVICE_EVENT_RECEIVED", "comanche.device.events.v1");

	private final String topicSuffix;
	private final String kafkaEventType;
	private final String kafkaTopic;

	MqttMessageType(String topicSuffix, String kafkaEventType, String kafkaTopic) {
		this.topicSuffix = topicSuffix;
		this.kafkaEventType = kafkaEventType;
		this.kafkaTopic = kafkaTopic;
	}

	public String topicSuffix() {
		return topicSuffix;
	}

	public String kafkaEventType() {
		return kafkaEventType;
	}

	public String kafkaTopic() {
		return kafkaTopic;
	}
}
