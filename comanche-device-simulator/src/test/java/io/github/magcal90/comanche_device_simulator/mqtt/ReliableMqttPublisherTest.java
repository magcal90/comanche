package io.github.magcal90.comanche_device_simulator.mqtt;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReliableMqttPublisherTest {
    @Test
    void publish_whenTransientFailuresOccur_shouldRetrySameEncodedEnvelope() throws Exception {
        var transport = new RecordingTransport(2);
        try (var publisher = publisher(transport, 4, 3)) {
            PublishResult result = publisher.publish(request("event-1", MessageCategory.DEVICE_EVENT))
                    .toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertThat(result.messageId()).isEqualTo("event-1");
            assertThat(transport.calls).hasSize(3);
            assertThat(transport.calls).extracting(call -> call.request().messageId()).containsOnly("event-1");
            assertThat(transport.calls).extracting(EncodedPublication::payload)
                    .allSatisfy(payload -> assertThat(payload).containsExactly(transport.calls.getFirst().payload()));
        }
    }

    @Test
    void publish_whenQueueIsFull_shouldCoalesceTelemetryButRejectUnbufferableOperationalEvent() {
        var transport = new BlockingTransport();
        try (var publisher = publisher(transport, 1, 1)) {
            publisher.publish(request("active", MessageCategory.DEVICE_EVENT));
            CompletionStage<PublishResult> oldTelemetry = publisher.publish(request("telemetry-old", MessageCategory.TELEMETRY));
            CompletionStage<PublishResult> newTelemetry = publisher.publish(request("telemetry-new", MessageCategory.TELEMETRY));

            assertFailure(oldTelemetry, PublicationCoalescedException.class);
            CompletionStage<PublishResult> bufferedEvent = publisher.publish(request("event-buffered", MessageCategory.DEVICE_EVENT));
            assertFailure(newTelemetry, PublicationCoalescedException.class);
            CompletionStage<PublishResult> rejected = publisher.publish(request("event-rejected", MessageCategory.DEVICE_EVENT));
            assertFailure(rejected, OutboundQueueFullException.class);
            assertThat(bufferedEvent.toCompletableFuture()).isNotCompleted();
        }
    }

    @Test
    void publish_whenOperationalEventArrivesToTelemetryFilledQueue_shouldEvictTelemetry() {
        var transport = new BlockingTransport();
        try (var publisher = publisher(transport, 1, 1)) {
            publisher.publish(request("active", MessageCategory.DEVICE_EVENT));
            CompletionStage<PublishResult> telemetry = publisher.publish(request("telemetry", MessageCategory.TELEMETRY));
            CompletionStage<PublishResult> event = publisher.publish(request("event", MessageCategory.DEVICE_EVENT));

            assertFailure(telemetry, PublicationCoalescedException.class);
            assertThat(event.toCompletableFuture()).isNotCompleted();
        }
    }

    private static ReliableMqttPublisher publisher(MqttTransport transport, int capacity, int attempts) {
        return new ReliableMqttPublisher(transport, new MqttEnvelopeSerializer(new ObjectMapper()), capacity,
                attempts, Duration.ofMillis(1), Duration.ofMillis(5));
    }

    private static PublishRequest request(String id, MessageCategory category) {
        Map<String, Object> payload = category == MessageCategory.TELEMETRY
                ? Map.of("metrics", Map.of("system.cpu.utilization", 25.0))
                : Map.of("eventType", "OPERATIONAL_STATE_CHANGED", "newState", "IDLE");
        return new PublishRequest(id, "KIOSK-001", Instant.parse("2026-10-08T04:00:00Z"), category, payload, 1);
    }

    private static void assertFailure(CompletionStage<?> stage, Class<? extends Throwable> type) {
        assertThatThrownBy(() -> stage.toCompletableFuture().get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(type);
    }

    private static final class RecordingTransport implements MqttTransport {
        private final int failures;
        private final List<EncodedPublication> calls = new ArrayList<>();
        private RecordingTransport(int failures) { this.failures = failures; }
        @Override public synchronized CompletionStage<Void> publish(EncodedPublication publication) {
            calls.add(publication);
            return calls.size() <= failures
                    ? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
                    : CompletableFuture.completedFuture(null);
        }
        @Override public void close() {}
    }

    private static final class BlockingTransport implements MqttTransport {
        @Override public CompletionStage<Void> publish(EncodedPublication publication) {
            return new CompletableFuture<>();
        }
        @Override public void close() {}
    }
}
