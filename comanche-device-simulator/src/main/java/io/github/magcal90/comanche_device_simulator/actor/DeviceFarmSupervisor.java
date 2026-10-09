package io.github.magcal90.comanche_device_simulator.actor;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.magcal90.comanche_device_simulator.config.SimulatorConfiguration;
import io.github.magcal90.comanche_device_simulator.identity.MessageIdGenerator;
import io.github.magcal90.comanche_device_simulator.mqtt.MqttPublisher;
import io.github.magcal90.comanche_device_simulator.simulation.SeedDeriver;
import io.github.magcal90.comanche_device_simulator.simulation.SimulationClock;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

public final class DeviceFarmSupervisor {
    private DeviceFarmSupervisor() {}

    public static Behavior<SupervisorCommand> create(
            SimulatorConfiguration configuration,
            SimulationClock clock,
            MqttPublisher publisher,
            MessageIdGenerator ids,
            boolean scheduleTicks) {
        return Behaviors.setup(context -> {
            Map<String, ActorRef<KioskCommand>> devices = new LinkedHashMap<>();
            configuration.kiosks().forEach(kiosk -> {
                Behavior<KioskCommand> behavior = KioskActor.create(kiosk,
                        SeedDeriver.forDevice(configuration.randomSeed(), kiosk.deviceId()), clock,
                        publisher, ids, context.getSelf(), configuration.telemetryIntervalTicks(),
                        configuration.statusIntervalTicks());
                ActorRef<KioskCommand> ref = context.spawn(
                        Behaviors.supervise(behavior).onFailure(SupervisorStrategy.stop()), kiosk.deviceId());
                devices.put(kiosk.deviceId(), ref);
            });
            Behavior<SupervisorCommand> running = Behaviors.receive(SupervisorCommand.class)
                    .onMessage(SupervisorCommand.TickAll.class, ignored -> {
                        devices.values().forEach(device -> device.tell(new KioskCommand.Tick()));
                        return Behaviors.same();
                    })
                    .onMessage(SupervisorCommand.GetDevice.class, command -> {
                        ActorRef<KioskCommand> ref = devices.get(command.deviceId());
                        if (ref != null) command.replyTo().tell(new SupervisorCommand.DeviceRef(command.deviceId(), ref));
                        return Behaviors.same();
                    })
                    .onMessage(SupervisorCommand.GetDeviceCount.class, command -> {
                        command.replyTo().tell(devices.size());
                        return Behaviors.same();
                    })
                    .onMessage(SupervisorCommand.PublicationExhausted.class, failure -> {
                        context.getLog().error("publication exhausted for device {} message {}",
                                failure.deviceId(), failure.messageId(), failure.cause());
                        return Behaviors.same();
                    })
                    .onMessage(SupervisorCommand.Stop.class, ignored -> Behaviors.stopped(() -> publisher.close()))
                    .build();
            if (!scheduleTicks) return running;
            return Behaviors.withTimers(timers -> {
                timers.startTimerAtFixedRate(new SupervisorCommand.TickAll(), configuration.tickInterval());
                return running;
            });
        });
    }
}
