package io.github.magcal90.comanche_device_simulator.actor;

import io.github.magcal90.comanche_device_simulator.model.DeviceSnapshot;
import io.github.magcal90.comanche_device_simulator.model.FaultType;
import io.github.magcal90.comanche_device_simulator.mqtt.PublishRequest;
import org.apache.pekko.actor.typed.ActorRef;

public sealed interface KioskCommand {
    record Tick() implements KioskCommand {}
    record InjectFault(FaultType faultType) implements KioskCommand {}
    record ClearFault(FaultType faultType) implements KioskCommand {}
    record GetSnapshot(ActorRef<DeviceSnapshot> replyTo) implements KioskCommand {}
    record PublishSucceeded(String messageId) implements KioskCommand {}
    record PublishFailed(PublishRequest request, Throwable cause) implements KioskCommand {}
}
