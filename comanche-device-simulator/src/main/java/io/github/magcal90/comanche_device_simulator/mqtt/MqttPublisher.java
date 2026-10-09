package io.github.magcal90.comanche_device_simulator.mqtt;

import java.util.concurrent.CompletionStage;

public interface MqttPublisher extends AutoCloseable {
    CompletionStage<PublishResult> publish(PublishRequest request);

    @Override
    default void close() {}
}
