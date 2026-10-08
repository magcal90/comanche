package io.github.magcal90.comanche_iot_gateway.contract;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

public record KafkaEventEnvelope(
		String eventId,
		String eventType,
		String schemaVersion,
		Instant occurredAt,
		Instant publishedAt,
		String source,
		String aggregateId,
		String correlationId,
		String sourceMessageId,
		String deviceId,
		JsonNode payload) {
}
