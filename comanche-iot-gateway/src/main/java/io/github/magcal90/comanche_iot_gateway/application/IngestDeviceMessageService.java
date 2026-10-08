package io.github.magcal90.comanche_iot_gateway.application;

import io.github.magcal90.comanche_iot_gateway.contract.KafkaEventEnvelope;
import io.github.magcal90.comanche_iot_gateway.contract.MqttEnvelope;
import io.github.magcal90.comanche_iot_gateway.mapping.MqttToKafkaEventMapper;
import io.github.magcal90.comanche_iot_gateway.mqtt.MqttTopic;
import io.github.magcal90.comanche_iot_gateway.mqtt.MqttTopicParser;
import io.github.magcal90.comanche_iot_gateway.port.DeviceEventPublisher;
import io.github.magcal90.comanche_iot_gateway.port.DeviceMessageIngestor;
import io.github.magcal90.comanche_iot_gateway.validation.MqttEnvelopeValidator;

import org.springframework.stereotype.Service;

@Service
public final class IngestDeviceMessageService implements DeviceMessageIngestor {

	private final MqttTopicParser topicParser;
	private final MqttEnvelopeValidator validator;
	private final MqttToKafkaEventMapper mapper;
	private final DeviceEventPublisher publisher;

	public IngestDeviceMessageService(
			MqttTopicParser topicParser,
			MqttEnvelopeValidator validator,
			MqttToKafkaEventMapper mapper,
			DeviceEventPublisher publisher) {
		this.topicParser = topicParser;
		this.validator = validator;
		this.mapper = mapper;
		this.publisher = publisher;
	}

	@Override
	public KafkaEventEnvelope ingest(String topicValue, byte[] payload) {
		MqttTopic topic = topicParser.parse(topicValue);
		MqttEnvelope envelope = validator.validate(topic, payload);
		KafkaEventEnvelope event = mapper.map(envelope);
		publisher.publish(envelope.messageType().kafkaTopic(), envelope.deviceId(), event);
		return event;
	}
}
