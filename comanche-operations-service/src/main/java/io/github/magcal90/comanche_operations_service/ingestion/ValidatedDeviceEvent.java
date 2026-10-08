package io.github.magcal90.comanche_operations_service.ingestion;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

record ValidatedDeviceEvent(
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
