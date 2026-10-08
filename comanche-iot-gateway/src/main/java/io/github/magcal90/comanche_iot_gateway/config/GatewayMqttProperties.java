package io.github.magcal90.comanche_iot_gateway.config;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("comanche.gateway.mqtt")
public record GatewayMqttProperties(
		boolean enabled,
		@NotNull URI serverUri,
		@NotBlank String clientId,
		String username,
		@NotNull Duration connectionTimeout,
		@NotNull Duration keepAliveInterval,
		@NotNull Duration maxReconnectDelay,
		@NotNull Duration completionTimeout) {

	@AssertTrue(message = "MQTT durations must be positive")
	boolean hasPositiveDurations() {
		return isPositive(connectionTimeout) && isPositive(keepAliveInterval)
				&& isPositive(maxReconnectDelay) && isPositive(completionTimeout);
	}

	private static boolean isPositive(Duration value) {
		return value != null && !value.isZero() && !value.isNegative();
	}
}
