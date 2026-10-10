package io.github.magcal90.comanche_device_simulator.actor;

import io.github.magcal90.comanche_device_simulator.model.FaultType;
import org.apache.pekko.actor.typed.ActorRef;

public sealed interface SupervisorCommand {
    record TickAll() implements SupervisorCommand {}
    record GetDevice(String deviceId, ActorRef<DeviceRef> replyTo) implements SupervisorCommand {}
    record GetDeviceCount(ActorRef<Integer> replyTo) implements SupervisorCommand {}
    record InjectFault(String deviceId, FaultType faultType) implements SupervisorCommand {}
    record ClearFault(String deviceId, FaultType faultType) implements SupervisorCommand {}
    record PublicationExhausted(String deviceId, String messageId, Throwable cause) implements SupervisorCommand {}
    record Stop() implements SupervisorCommand {}
    record DeviceRef(String deviceId, ActorRef<KioskCommand> actorRef) {}
}
