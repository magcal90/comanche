package io.github.magcal90.comanche_device_simulator.mqtt;

public final class OutboundQueueFullException extends RuntimeException {
    public OutboundQueueFullException(String message) { super(message); }
}
