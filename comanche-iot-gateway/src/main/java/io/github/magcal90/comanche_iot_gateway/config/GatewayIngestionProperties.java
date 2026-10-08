package io.github.magcal90.comanche_iot_gateway.config;

import java.time.Duration;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("comanche.gateway.ingestion")
public record GatewayIngestionProperties(
		@NotNull Set<String> registeredDeviceIds,
		@NotNull DataSize maxMessageSize,
		@NotNull Duration kafkaAckTimeout) {

	public GatewayIngestionProperties {
		registeredDeviceIds = registeredDeviceIds == null ? Set.of() : Set.copyOf(registeredDeviceIds);
	}

	@AssertTrue(message = "message size and Kafka acknowledgement timeout must be positive")
	boolean hasPositiveLimits() {
		return maxMessageSize != null && maxMessageSize.toBytes() > 0
				&& kafkaAckTimeout != null && !kafkaAckTimeout.isZero() && !kafkaAckTimeout.isNegative();
	}
}
