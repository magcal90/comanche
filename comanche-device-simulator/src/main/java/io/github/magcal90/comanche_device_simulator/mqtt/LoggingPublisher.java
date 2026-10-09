package io.github.magcal90.comanche_device_simulator.mqtt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Non-network checkpoint adapter that makes attempted publications visible. */
public final class LoggingPublisher implements MqttPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingPublisher.class);

    @Override
    public CompletionStage<PublishResult> publish(PublishRequest request) {
        LOGGER.info("simulated publication deviceId={} category={} messageId={} attempt={}",
                request.deviceId(), request.category(), request.messageId(), request.attempt());
        return CompletableFuture.completedFuture(new PublishResult(request.messageId()));
    }
}
