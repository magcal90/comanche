package io.github.magcal90.comanche_device_simulator.actor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.magcal90.comanche_device_simulator.mqtt.MqttPublisher;
import io.github.magcal90.comanche_device_simulator.mqtt.PublishRequest;
import io.github.magcal90.comanche_device_simulator.mqtt.PublishResult;

final class FakeMqttPublisher implements MqttPublisher {
    private final List<PublishRequest> requests = new CopyOnWriteArrayList<>();
    private volatile boolean fail;

    @Override
    public CompletionStage<PublishResult> publish(PublishRequest request) {
        requests.add(request);
        if (fail) return CompletableFuture.failedFuture(new IllegalStateException("simulated failure"));
        return CompletableFuture.completedFuture(new PublishResult(request.messageId()));
    }

    List<PublishRequest> requests() { return List.copyOf(requests); }
    void failPublications() { fail = true; }
}
