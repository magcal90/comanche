package io.github.magcal90.comanche_iot_gateway.mqtt;

import io.github.magcal90.comanche_iot_gateway.port.DeviceMessageIngestor;
import io.github.magcal90.comanche_iot_gateway.observability.GatewayMetrics;
import io.github.magcal90.comanche_iot_gateway.validation.InvalidDeviceMessageException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.StaticMessageHeaderAccessor;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public final class MqttInboundMessageHandler {

	private static final Logger logger = LoggerFactory.getLogger(MqttInboundMessageHandler.class);
	private final DeviceMessageIngestor ingestionService;
	private final GatewayMetrics metrics;

	public MqttInboundMessageHandler(DeviceMessageIngestor ingestionService, GatewayMetrics metrics) {
		this.ingestionService = ingestionService;
		this.metrics = metrics;
	}

	public void handle(Message<byte[]> message) {
		String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
		SimpleAcknowledgment acknowledgment = StaticMessageHeaderAccessor.getAcknowledgment(message);
		if (acknowledgment == null) {
			throw new IllegalStateException("MQTT manual acknowledgement callback is missing");
		}
		metrics.messageReceived();
		try {
			var event = ingestionService.ingest(topic, message.getPayload());
			acknowledgment.acknowledge();
			metrics.eventPublished();
			logger.atDebug()
					.addKeyValue("eventId", event.eventId())
					.addKeyValue("deviceId", event.deviceId())
					.addKeyValue("eventType", event.eventType())
					.log("MQTT message published to Kafka and acknowledged");
		}
		catch (InvalidDeviceMessageException ex) {
			acknowledgment.acknowledge();
			metrics.messageRejected(ex.reason());
			logger.atWarn()
					.addKeyValue("topic", topic)
					.addKeyValue("rejectionReason", ex.reason())
					.log("Rejected invalid MQTT message");
		}
		catch (RuntimeException ex) {
			metrics.publicationFailed();
			throw ex;
		}
	}
}
