package io.github.magcal90.comanche_device_simulator.identity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Checkpoint-A generator. UUIDv7 replaces this adapter in the MQTT checkpoint. */
public final class InProcessMessageIdGenerator implements MessageIdGenerator {
    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    @Override
    public String nextId(String deviceId) {
        long sequence = sequences.computeIfAbsent(deviceId, ignored -> new AtomicLong()).incrementAndGet();
        return deviceId + "-checkpoint-a-" + sequence;
    }
}
