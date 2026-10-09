package io.github.magcal90.comanche_device_simulator.identity;

@FunctionalInterface
public interface MessageIdGenerator {
    String nextId(String deviceId);
}
