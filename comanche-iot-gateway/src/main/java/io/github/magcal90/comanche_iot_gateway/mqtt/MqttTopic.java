package io.github.magcal90.comanche_iot_gateway.mqtt;

public record MqttTopic(String deviceId, MqttMessageType messageType, String value) {
}
