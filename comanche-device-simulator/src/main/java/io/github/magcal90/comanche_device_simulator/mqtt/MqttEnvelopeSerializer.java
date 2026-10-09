package io.github.magcal90.comanche_device_simulator.mqtt;

import java.nio.charset.StandardCharsets;

import io.github.magcal90.comanche_device_simulator.contract.MqttEnvelope;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class MqttEnvelopeSerializer {
    private static final String SCHEMA_VERSION = "1.0";
    private final ObjectMapper mapper;

    public MqttEnvelopeSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public byte[] serialize(PublishRequest request) {
        MqttEnvelope envelope = new MqttEnvelope(SCHEMA_VERSION, request.messageId(), request.deviceId(),
                request.eventTime().toString(), request.category().name(), request.payload());
        try {
            return mapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8);
        }
        catch (JacksonException exception) {
            throw new IllegalArgumentException("Cannot serialize MQTT envelope " + request.messageId(), exception);
        }
    }
}
