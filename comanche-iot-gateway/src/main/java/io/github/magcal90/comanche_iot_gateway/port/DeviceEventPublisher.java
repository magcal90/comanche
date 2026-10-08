package io.github.magcal90.comanche_iot_gateway.port;

import io.github.magcal90.comanche_iot_gateway.contract.KafkaEventEnvelope;

public interface DeviceEventPublisher {

	void publish(String topic, String key, KafkaEventEnvelope event);
}
