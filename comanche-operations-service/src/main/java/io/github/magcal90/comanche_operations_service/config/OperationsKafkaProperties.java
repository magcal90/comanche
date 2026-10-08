package io.github.magcal90.comanche_operations_service.config;

import java.time.Duration;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("comanche.operations.kafka.retry")
public record OperationsKafkaProperties(
		@Min(1) int maxAttempts,
		@NotNull Duration initialBackoff,
		@DecimalMin("1.0") double multiplier,
		@NotNull Duration maxBackoff,
		@NotNull Duration deadLetterPublishTimeout) {
}
