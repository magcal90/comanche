package io.github.magcal90.comanche_device_simulator.actor;

import org.apache.pekko.actor.typed.ActorRef;

public sealed interface SupervisorCommand {
    record TickAll() implements SupervisorCommand {}
    record GetDevice(String deviceId, ActorRef<DeviceRef> replyTo) implements SupervisorCommand {}
    record GetDeviceCount(ActorRef<Integer> replyTo) implements SupervisorCommand {}
    record PublicationExhausted(String deviceId, String messageId, Throwable cause) implements SupervisorCommand {}
    record Stop() implements SupervisorCommand {}
    record DeviceRef(String deviceId, ActorRef<KioskCommand> actorRef) {}
}
