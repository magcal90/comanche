package io.github.magcal90.comanche_iot_gateway.observability;

import io.github.magcal90.comanche_iot_gateway.validation.RejectionReason;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public final class GatewayMetrics {

	private final MeterRegistry registry;

	public GatewayMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void messageReceived() {
		registry.counter("comanche.gateway.mqtt.messages.received").increment();
	}

	public void messageRejected(RejectionReason reason) {
		registry.counter("comanche.gateway.mqtt.messages.rejected", "reason", reason.name()).increment();
	}

	public void eventPublished() {
		registry.counter("comanche.gateway.kafka.events.published").increment();
	}

	public void publicationFailed() {
		registry.counter("comanche.gateway.kafka.publication.failures").increment();
	}
}
