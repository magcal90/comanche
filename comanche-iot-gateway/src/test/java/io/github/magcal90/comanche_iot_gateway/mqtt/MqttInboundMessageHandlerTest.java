package io.github.magcal90.comanche_iot_gateway.mqtt;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.magcal90.comanche_iot_gateway.contract.KafkaEventEnvelope;
import io.github.magcal90.comanche_iot_gateway.kafka.KafkaPublicationException;
import io.github.magcal90.comanche_iot_gateway.port.DeviceMessageIngestor;
import io.github.magcal90.comanche_iot_gateway.observability.GatewayMetrics;
import io.github.magcal90.comanche_iot_gateway.validation.InvalidDeviceMessageException;
import io.github.magcal90.comanche_iot_gateway.validation.RejectionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttInboundMessageHandlerTest {

	@Mock
	private DeviceMessageIngestor ingestionService;

	@Test
	void handle_whenKafkaBrokerAcknowledges_shouldAcknowledgeMqttDelivery() throws Exception {
		AtomicBoolean acknowledged = new AtomicBoolean();
		var message = mqttMessage(acknowledged);
		when(ingestionService.ingest(message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class), message.getPayload()))
				.thenReturn(event());

		new MqttInboundMessageHandler(ingestionService, metrics()).handle(message);

		assertThat(acknowledged).isTrue();
	}

	@Test
	void handle_whenKafkaPublicationFails_shouldNotAcknowledgeMqttDelivery() {
		AtomicBoolean acknowledged = new AtomicBoolean();
		var message = mqttMessage(acknowledged);
		when(ingestionService.ingest(message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class), message.getPayload()))
				.thenThrow(new KafkaPublicationException("not acknowledged", new RuntimeException("broker unavailable")));

		assertThatThrownBy(() -> new MqttInboundMessageHandler(ingestionService, metrics()).handle(message))
				.isInstanceOf(KafkaPublicationException.class);
		assertThat(acknowledged).isFalse();
	}

	@Test
	void handle_whenMessageIsPermanentlyInvalid_shouldAcknowledgeAndDiscardIt() {
		AtomicBoolean acknowledged = new AtomicBoolean();
		var message = mqttMessage(acknowledged);
		when(ingestionService.ingest(message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class), message.getPayload()))
				.thenThrow(new InvalidDeviceMessageException(RejectionReason.MALFORMED_JSON, "invalid"));

		new MqttInboundMessageHandler(ingestionService, metrics()).handle(message);

		assertThat(acknowledged).isTrue();
	}

	private static org.springframework.messaging.Message<byte[]> mqttMessage(AtomicBoolean acknowledged) {
		return MessageBuilder.withPayload("{}".getBytes())
				.setHeader(MqttHeaders.RECEIVED_TOPIC, "comanche/devices/KIOSK-TAM-003/telemetry")
				.setHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK,
						(org.springframework.integration.acks.SimpleAcknowledgment) () -> acknowledged.set(true))
				.build();
	}

	private static KafkaEventEnvelope event() throws Exception {
		Instant now = Instant.parse("2026-10-08T04:00:00Z");
		return new KafkaEventEnvelope("event-1", "DEVICE_TELEMETRY_RECEIVED", "1.0", now, now,
				"comanche-iot-gateway", "KIOSK-TAM-003", "msg-1", "msg-1", "KIOSK-TAM-003",
				new ObjectMapper().readTree("{}"));
	}

	private static GatewayMetrics metrics() {
		return new GatewayMetrics(new SimpleMeterRegistry());
	}
}
