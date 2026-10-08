package io.github.magcal90.comanche_iot_gateway.kafka;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.github.magcal90.comanche_iot_gateway.config.GatewayIngestionProperties;
import io.github.magcal90.comanche_iot_gateway.contract.KafkaEventEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaDeviceEventPublisherTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@Test
	void publish_whenKafkaBrokerRejectsSend_shouldExposePublicationFailure() throws Exception {
		CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failed =
				CompletableFuture.failedFuture(new RuntimeException("broker unavailable"));
		when(kafkaTemplate.send(
				org.mockito.ArgumentMatchers.eq("comanche.device.telemetry.v1"),
				org.mockito.ArgumentMatchers.eq("KIOSK-1"),
				org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(failed);
		var publisher = new KafkaDeviceEventPublisher(kafkaTemplate, new ObjectMapper(),
				new GatewayIngestionProperties(Set.of("KIOSK-1"), DataSize.ofKilobytes(32), Duration.ofSeconds(1)));

		assertThatThrownBy(() -> publisher.publish("comanche.device.telemetry.v1", "KIOSK-1", event()))
				.isInstanceOf(KafkaPublicationException.class)
				.hasMessageContaining("did not acknowledge");
	}

	private static KafkaEventEnvelope event() throws Exception {
		Instant now = Instant.parse("2026-10-08T04:00:00Z");
		return new KafkaEventEnvelope("event-1", "DEVICE_TELEMETRY_RECEIVED", "1.0", now, now,
				"comanche-iot-gateway", "KIOSK-1", "msg-1", "msg-1", "KIOSK-1",
				new ObjectMapper().readTree("{}"));
	}
}
