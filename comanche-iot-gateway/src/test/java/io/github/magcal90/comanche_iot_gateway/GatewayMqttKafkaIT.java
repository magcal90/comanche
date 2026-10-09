package io.github.magcal90.comanche_iot_gateway;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.magcal90.comanche_iot_gateway.kafka.KafkaPublicationException;
import io.github.magcal90.comanche_iot_gateway.kafka.KafkaDeviceEventPublisher;
import io.github.magcal90.comanche_iot_gateway.mqtt.MqttInboundMessageHandler;
import io.github.magcal90.comanche_iot_gateway.config.GatewayIngestionProperties;
import io.github.magcal90.comanche_iot_gateway.contract.KafkaEventEnvelope;
import io.github.magcal90.comanche_iot_gateway.observability.GatewayMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayMqttKafkaIT {

	private static final String DEVICE_ID = "KIOSK-TAM-003";
	private static final String TELEMETRY_TOPIC = "comanche.device.telemetry.v1";

	@Container
	static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.0"));

	@SuppressWarnings("resource")
	@Container
	static final GenericContainer<?> MQTT = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.22"))
			.withCopyFileToContainer(MountableFile.forClasspathResource("mosquitto-it.conf"),
					"/mosquitto/config/mosquitto.conf")
			.withExposedPorts(1883);

	@DynamicPropertySource
	static void gatewayProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
		registry.add("spring.kafka.producer.properties.request.timeout.ms", () -> "1000");
		registry.add("spring.kafka.producer.properties.delivery.timeout.ms", () -> "3000");
		registry.add("spring.kafka.producer.properties.max.block.ms", () -> "2000");
		registry.add("comanche.gateway.mqtt.enabled", () -> "true");
		registry.add("comanche.gateway.mqtt.server-uri",
				() -> "tcp://" + MQTT.getHost() + ':' + MQTT.getMappedPort(1883));
		registry.add("comanche.gateway.mqtt.client-id", () -> "gateway-it-1");
		registry.add("comanche.gateway.mqtt.username", () -> "integration-test");
		registry.add("comanche.gateway.mqtt.password", () -> "integration-test");
		registry.add("comanche.gateway.ingestion.registered-device-ids", () -> DEVICE_ID);
		registry.add("comanche.gateway.ingestion.kafka-ack-timeout", () -> "5s");
	}

	@Test
	@Order(1)
	void mqttToKafka_whenMessageIsDeliveredTwice_shouldPublishStableLogicalIdentity() throws Exception {
		try (KafkaConsumer<String, String> consumer = consumer()) {
			consumer.subscribe(List.of(TELEMETRY_TOPIC));
			consumer.poll(Duration.ofMillis(500));
			publishTelemetry("tel-duplicate-1");
			publishTelemetry("tel-duplicate-1");

			List<String> values = pollValues(consumer, 2, Duration.ofSeconds(20));
			assertThat(values).hasSize(2);
			ObjectMapper mapper = new ObjectMapper();
			assertThat(mapper.readTree(values.get(0)).get("eventId").stringValue())
					.isEqualTo(mapper.readTree(values.get(1)).get("eventId").stringValue());
			assertThat(mapper.readTree(values.get(0)).get("sourceMessageId").stringValue())
					.isEqualTo("tel-duplicate-1");
		}
	}

	@Test
	@Order(2)
	void mqttAcknowledgement_whenKafkaBrokerIsUnavailable_shouldRemainIncomplete() throws Exception {
		AtomicBoolean acknowledged = new AtomicBoolean();
		var message = MessageBuilder.withPayload(telemetry("tel-failure-1").getBytes(StandardCharsets.UTF_8))
				.setHeader(MqttHeaders.RECEIVED_TOPIC, "comanche/devices/" + DEVICE_ID + "/telemetry")
				.setHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK,
						(SimpleAcknowledgment) () -> acknowledged.set(true))
				.build();

		Map<String, Object> producerProperties = Map.of(
				"bootstrap.servers", "127.0.0.1:1",
				"key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
				"value.serializer", "org.apache.kafka.common.serialization.StringSerializer",
				"acks", "all",
				"enable.idempotence", true,
				"max.block.ms", 1000,
				"request.timeout.ms", 1000,
				"delivery.timeout.ms", 2000);
		DefaultKafkaProducerFactory<String, String> producerFactory =
				new DefaultKafkaProducerFactory<>(producerProperties);
		KafkaTemplate<String, String> unavailableKafka = new KafkaTemplate<>(producerFactory);
		try {
			var publisher = new KafkaDeviceEventPublisher(unavailableKafka, new ObjectMapper(),
					new GatewayIngestionProperties(Set.of(DEVICE_ID), DataSize.ofKilobytes(32), Duration.ofSeconds(3)));
			KafkaEventEnvelope event = new KafkaEventEnvelope(
					"event-failure-1", "DEVICE_TELEMETRY_RECEIVED", "1.0",
					Instant.parse("2026-10-08T04:00:00Z"), Instant.parse("2026-10-08T04:00:01Z"),
					"comanche-iot-gateway", DEVICE_ID, "tel-failure-1", "tel-failure-1", DEVICE_ID,
					new ObjectMapper().readTree("{}"));
			MqttInboundMessageHandler failureHandler = new MqttInboundMessageHandler((topic, payload) -> {
				publisher.publish(TELEMETRY_TOPIC, DEVICE_ID, event);
				return event;
			}, new GatewayMetrics(new SimpleMeterRegistry()));

			assertThatThrownBy(() -> failureHandler.handle(message))
					.isInstanceOf(KafkaPublicationException.class);
			assertThat(acknowledged).isFalse();
		}
		finally {
			unavailableKafka.destroy();
			producerFactory.destroy();
		}
	}

	private static void publishTelemetry(String messageId) throws Exception {
		String broker = "tcp://" + MQTT.getHost() + ':' + MQTT.getMappedPort(1883);
		try (MqttClient client = new MqttClient(broker, "device-it-" + UUID.randomUUID())) {
			MqttConnectOptions options = new MqttConnectOptions();
			options.setCleanSession(true);
			client.connect(options);
			MqttMessage message = new MqttMessage(telemetry(messageId).getBytes(StandardCharsets.UTF_8));
			message.setQos(1);
			client.publish("comanche/devices/" + DEVICE_ID + "/telemetry", message);
			client.disconnect();
		}
	}

	private static String telemetry(String messageId) {
		return """
				{"schemaVersion":"1.0","messageId":"%s","deviceId":"%s",
				 "eventTime":"2026-10-08T04:00:00Z","messageType":"TELEMETRY",
				 "payload":{"metrics":{"printer.temperature":72.0,"printer.errorRate":0.19}}}
				""".formatted(messageId, DEVICE_ID);
	}

	private static KafkaConsumer<String, String> consumer() {
		Properties properties = new Properties();
		properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, "gateway-it-" + UUID.randomUUID());
		properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		return new KafkaConsumer<>(properties);
	}

	private static List<String> pollValues(
			KafkaConsumer<String, String> consumer, int expected, Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		List<String> values = new ArrayList<>();
		while (values.size() < expected && System.nanoTime() < deadline) {
			consumer.poll(Duration.ofMillis(500)).forEach(record -> values.add(record.value()));
		}
		return values;
	}
}
