package io.github.magcal90.comanche_iot_gateway.port;

import io.github.magcal90.comanche_iot_gateway.contract.KafkaEventEnvelope;

public interface DeviceMessageIngestor {

	KafkaEventEnvelope ingest(String topic, byte[] payload);
}
