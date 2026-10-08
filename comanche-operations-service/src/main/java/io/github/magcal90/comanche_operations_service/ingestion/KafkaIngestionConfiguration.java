package io.github.magcal90.comanche_operations_service.ingestion;

import java.time.Clock;
import java.util.Map;

import io.github.magcal90.comanche_operations_service.config.OperationsKafkaProperties;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration(proxyBeanMethods = false)
class KafkaIngestionConfiguration {

	private static final Map<String, String> DEAD_LETTER_TOPICS = Map.of(
			KafkaEventValidator.TELEMETRY_TOPIC, "comanche.device.telemetry.dlt.v1",
			KafkaEventValidator.STATUS_TOPIC, "comanche.device.status.dlt.v1",
			KafkaEventValidator.DEVICE_EVENTS_TOPIC, "comanche.device.events.dlt.v1");

	@Bean
	Clock operationsClock() {
		return Clock.systemUTC();
	}

	@Bean
	DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
			KafkaTemplate<Object, Object> template, OperationsKafkaProperties properties) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, exception) -> {
			String destination = DEAD_LETTER_TOPICS.get(record.topic());
			if (destination == null) throw new IllegalArgumentException("No dead-letter topic for " + record.topic());
			return new TopicPartition(destination, record.partition());
		});
		recoverer.setFailIfSendResultIsError(true);
		recoverer.setWaitForSendResultTimeout(properties.deadLetterPublishTimeout());
		recoverer.setAppendOriginalHeaders(true);
		recoverer.setStripPreviousExceptionHeaders(false);
		return recoverer;
	}

	@Bean
	DefaultErrorHandler kafkaErrorHandler(
			DeadLetterPublishingRecoverer recoverer, OperationsKafkaProperties properties) {
		ExponentialBackOffWithMaxRetries backOff =
				new ExponentialBackOffWithMaxRetries(properties.maxAttempts() - 1);
		backOff.setInitialInterval(properties.initialBackoff().toMillis());
		backOff.setMultiplier(properties.multiplier());
		backOff.setMaxInterval(properties.maxBackoff().toMillis());
		DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
		handler.addNotRetryableExceptions(
				InvalidEventException.class, UnknownDeviceException.class, EventIdentityConflictException.class);
		handler.setCommitRecovered(true);
		handler.setAckAfterHandle(true);
		return handler;
	}
}
