package io.github.magcal90.comanche_device_simulator.mqtt;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MqttEnvelopeSerializerTest {
    @Test
    void serialize_shouldProduceGatewayCompatibleVersionedEnvelope() throws Exception {
        var request = new PublishRequest("fixed-id", "KIOSK-001", Instant.parse("2026-10-08T04:00:00Z"),
                MessageCategory.DEVICE_EVENT, Map.of(
                        "eventType", "OPERATIONAL_STATE_CHANGED",
                        "previousState", "IDLE",
                        "newState", "TAKING_ORDER"), 1);

        var root = new ObjectMapper().readTree(new MqttEnvelopeSerializer(new ObjectMapper()).serialize(request));

        assertThat(root.get("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(root.get("messageId").asText()).isEqualTo("fixed-id");
        assertThat(root.get("deviceId").asText()).isEqualTo("KIOSK-001");
        assertThat(root.get("eventTime").asText()).isEqualTo("2026-10-08T04:00:00Z");
        assertThat(root.get("messageType").asText()).isEqualTo("DEVICE_EVENT");
        assertThat(root.get("payload").get("newState").asText()).isEqualTo("TAKING_ORDER");
        assertThat(MqttTopics.forCategory("KIOSK-001", MessageCategory.DEVICE_EVENT))
                .isEqualTo("comanche/devices/KIOSK-001/events");
    }
}
