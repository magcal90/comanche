package io.github.magcal90.comanche_device_simulator.mqtt;

public record EncodedPublication(
        PublishRequest request,
        String topic,
        byte[] payload,
        int qos,
        boolean retained) {
    public EncodedPublication {
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
