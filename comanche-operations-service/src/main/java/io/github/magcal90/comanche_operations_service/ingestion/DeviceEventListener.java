package io.github.magcal90.comanche_operations_service.ingestion;

import java.time.Clock;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
final class DeviceEventListener {

	private final DeviceIngestionService service;
	private final Clock clock;

	DeviceEventListener(DeviceIngestionService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@KafkaListener(topics = {
			KafkaEventValidator.TELEMETRY_TOPIC,
			KafkaEventValidator.STATUS_TOPIC,
			KafkaEventValidator.DEVICE_EVENTS_TOPIC
	})
	void onEvent(ConsumerRecord<String, String> record) {
		service.process(record.topic(), record.value(), clock.instant());
	}
}
