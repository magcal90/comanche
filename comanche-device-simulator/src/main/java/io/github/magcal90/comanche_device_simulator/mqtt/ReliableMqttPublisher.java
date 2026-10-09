package io.github.magcal90.comanche_device_simulator.mqtt;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ReliableMqttPublisher implements MqttPublisher {
    private final MqttTransport transport;
    private final MqttEnvelopeSerializer serializer;
    private final int capacity;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final ScheduledExecutorService scheduler;
    private final Deque<Pending> queue = new ArrayDeque<>();
    private boolean active;
    private volatile boolean closed;
    private Pending current;

    public ReliableMqttPublisher(
            MqttTransport transport,
            MqttEnvelopeSerializer serializer,
            int capacity,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff) {
        this.transport = transport;
        this.serializer = serializer;
        this.capacity = capacity;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "simulator-mqtt-publisher");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public synchronized CompletionStage<PublishResult> publish(PublishRequest request) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("MQTT publisher is closed"));
        CompletableFuture<PublishResult> result = new CompletableFuture<>();
        Pending pending = encode(request, result);
        if (active && queue.size() >= capacity && !makeRoom(pending)) {
            result.completeExceptionally(new OutboundQueueFullException(
                    "Outbound queue is full for " + request.category() + " " + request.messageId()));
            return result;
        }
        if (active) queue.addLast(pending);
        else {
            active = true;
            current = pending;
            dispatch(pending, 1);
        }
        return result;
    }

    private Pending encode(PublishRequest request, CompletableFuture<PublishResult> result) {
        boolean retained = request.category() == MessageCategory.STATUS;
        return new Pending(new EncodedPublication(request, MqttTopics.forCategory(request.deviceId(), request.category()),
                serializer.serialize(request), 1, retained), result);
    }

    private boolean makeRoom(Pending incoming) {
        if (incoming.publication.request().category() == MessageCategory.TELEMETRY) {
            Pending replaced = removeFirst(candidate -> candidate.publication.request().category() == MessageCategory.TELEMETRY
                    && candidate.publication.request().deviceId().equals(incoming.publication.request().deviceId()));
            if (replaced == null) return false;
            replaced.result.completeExceptionally(new PublicationCoalescedException(
                    "Telemetry replaced by a newer observation for " + incoming.publication.request().deviceId()));
            return true;
        }
        if (incoming.publication.request().category() == MessageCategory.STATUS) {
            Pending replaced = removeFirst(candidate -> candidate.publication.request().category() == MessageCategory.STATUS
                    && candidate.publication.request().deviceId().equals(incoming.publication.request().deviceId()));
            if (replaced != null) {
                replaced.result.completeExceptionally(new PublicationCoalescedException(
                        "Status replaced by a newer observation for " + incoming.publication.request().deviceId()));
                return true;
            }
        }
        Pending telemetry = removeFirst(candidate -> candidate.publication.request().category() == MessageCategory.TELEMETRY);
        if (telemetry == null) return false;
        telemetry.result.completeExceptionally(new PublicationCoalescedException(
                "Telemetry evicted to preserve " + incoming.publication.request().category()));
        return true;
    }

    private Pending removeFirst(java.util.function.Predicate<Pending> predicate) {
        Iterator<Pending> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Pending candidate = iterator.next();
            if (predicate.test(candidate)) {
                iterator.remove();
                return candidate;
            }
        }
        return null;
    }

    private void dispatch(Pending pending, int attempt) {
        transport.publish(pending.publication).whenComplete((ignored, failure) -> {
            if (failure == null) {
                pending.result.complete(new PublishResult(pending.publication.request().messageId()));
                completeCurrent();
            }
            else if (attempt < maxAttempts && !closed) {
                long delay = backoff(attempt).toMillis();
                scheduler.schedule(() -> dispatch(pending, attempt + 1), delay, TimeUnit.MILLISECONDS);
            }
            else {
                pending.result.completeExceptionally(failure);
                completeCurrent();
            }
        });
    }

    private Duration backoff(int failedAttempt) {
        long multiplier = 1L << Math.min(failedAttempt - 1, 30);
        long millis;
        try {
            millis = Math.multiplyExact(initialBackoff.toMillis(), multiplier);
        }
        catch (ArithmeticException exception) {
            millis = Long.MAX_VALUE;
        }
        return Duration.ofMillis(Math.min(millis, maxBackoff.toMillis()));
    }

    private synchronized void completeCurrent() {
        Pending next = queue.pollFirst();
        current = next;
        if (next == null) active = false;
        else dispatch(next, 1);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        IllegalStateException failure = new IllegalStateException("MQTT publisher closed before delivery");
        if (current != null) current.result.completeExceptionally(failure);
        queue.forEach(pending -> pending.result.completeExceptionally(failure));
        queue.clear();
        current = null;
        scheduler.shutdownNow();
        transport.close();
    }

    private record Pending(EncodedPublication publication, CompletableFuture<PublishResult> result) {}
}
