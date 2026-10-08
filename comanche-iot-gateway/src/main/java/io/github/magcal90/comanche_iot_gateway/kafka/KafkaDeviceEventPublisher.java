package io.github.magcal90.comanche_iot_gateway.kafka;

import java.util.concurrent.TimeUnit;

import io.github.magcal90.comanche_iot_gateway.config.GatewayIngestionProperties;
import io.github.magcal90.comanche_iot_gateway.contract.KafkaEventEnvelope;
import io.github.magcal90.comanche_iot_gateway.port.DeviceEventPublisher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public final class KafkaDeviceEventPublisher implements DeviceEventPublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final GatewayIngestionProperties properties;

	public KafkaDeviceEventPublisher(
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper,
			GatewayIngestionProperties properties) {
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Override
	public void publish(String topic, String key, KafkaEventEnvelope event) {
		try {
			String value = objectMapper.writeValueAsString(event);
			kafkaTemplate.send(topic, key, value).get(properties.kafkaAckTimeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (JacksonException ex) {
			throw new KafkaPublicationException("Unable to serialize validated gateway event", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new KafkaPublicationException("Interrupted while awaiting Kafka broker acknowledgement", ex);
		}
		catch (Exception ex) {
			throw new KafkaPublicationException("Kafka broker did not acknowledge gateway event", ex);
		}
	}
}
