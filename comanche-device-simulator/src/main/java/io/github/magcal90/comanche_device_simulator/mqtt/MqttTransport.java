package io.github.magcal90.comanche_device_simulator.mqtt;

import java.util.concurrent.CompletionStage;

public interface MqttTransport extends AutoCloseable {
    CompletionStage<Void> publish(EncodedPublication publication);
    @Override void close();
}
