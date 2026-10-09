package io.github.magcal90.comanche_device_simulator.actor;

import java.util.Map;

import io.github.magcal90.comanche_device_simulator.config.KioskConfiguration;
import io.github.magcal90.comanche_device_simulator.identity.MessageIdGenerator;
import io.github.magcal90.comanche_device_simulator.model.DeviceSnapshot;
import io.github.magcal90.comanche_device_simulator.model.KioskState;
import io.github.magcal90.comanche_device_simulator.mqtt.MessageCategory;
import io.github.magcal90.comanche_device_simulator.mqtt.MqttPublisher;
import io.github.magcal90.comanche_device_simulator.mqtt.PublishRequest;
import io.github.magcal90.comanche_device_simulator.simulation.KioskSimulation;
import io.github.magcal90.comanche_device_simulator.simulation.SimulationClock;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;

public final class KioskActor extends AbstractBehavior<KioskCommand> {
    private final SimulationClock clock;
    private final MqttPublisher publisher;
    private final MessageIdGenerator ids;
    private final ActorRef<SupervisorCommand> supervisor;
    private final int telemetryIntervalTicks;
    private final int statusIntervalTicks;
    private KioskState state;

    private KioskActor(
            ActorContext<KioskCommand> context,
            KioskConfiguration config,
            long seed,
            SimulationClock clock,
            MqttPublisher publisher,
            MessageIdGenerator ids,
            ActorRef<SupervisorCommand> supervisor,
            int telemetryIntervalTicks,
            int statusIntervalTicks) {
        super(context);
        this.clock = clock;
        this.publisher = publisher;
        this.ids = ids;
        this.supervisor = supervisor;
        this.telemetryIntervalTicks = telemetryIntervalTicks;
        this.statusIntervalTicks = statusIntervalTicks;
        this.state = KioskSimulation.initial(config.deviceId(), config.siteId(), seed, clock);
    }

    public static Behavior<KioskCommand> create(
            KioskConfiguration config,
            long seed,
            SimulationClock clock,
            MqttPublisher publisher,
            MessageIdGenerator ids,
            ActorRef<SupervisorCommand> supervisor,
            int telemetryIntervalTicks,
            int statusIntervalTicks) {
        return Behaviors.setup(context -> new KioskActor(context, config, seed, clock, publisher, ids,
                supervisor, telemetryIntervalTicks, statusIntervalTicks));
    }

    @Override
    public Receive<KioskCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(KioskCommand.Tick.class, ignored -> onTick())
                .onMessage(KioskCommand.GetSnapshot.class, this::onGetSnapshot)
                .onMessage(KioskCommand.PublishSucceeded.class, ignored -> this)
                .onMessage(KioskCommand.PublishFailed.class, this::onPublishFailed)
                .build();
    }

    private Behavior<KioskCommand> onTick() {
        KioskState previous = state;
        state = KioskSimulation.advance(state, clock);
        if (previous.operationalState() != state.operationalState()) {
            publish(MessageCategory.DEVICE_EVENT, Map.of(
                    "eventType", "OPERATIONAL_STATE_CHANGED",
                    "previousState", previous.operationalState().name(),
                    "newState", state.operationalState().name()));
        }
        if (state.tick() % telemetryIntervalTicks == 0) {
            publish(MessageCategory.TELEMETRY, Map.of("metrics", Map.of(
                    "system.cpu.utilization", state.cpuUtilization(),
                    "system.memory.utilization", state.memoryUtilization())));
        }
        if (state.tick() % statusIntervalTicks == 0) {
            publish(MessageCategory.STATUS, Map.of(
                    "connectionStatus", "ONLINE",
                    "applicationStatus", "RUNNING"));
        }
        return this;
    }

    private Behavior<KioskCommand> onGetSnapshot(KioskCommand.GetSnapshot command) {
        command.replyTo().tell(new DeviceSnapshot(state));
        return this;
    }

    private void publish(MessageCategory category, Map<String, Object> payload) {
        PublishRequest request = new PublishRequest(ids.nextId(state.deviceId()), state.deviceId(),
                state.simulationTime(), category, payload, 1);
        submit(request);
    }

    private void submit(PublishRequest request) {
        getContext().pipeToSelf(publisher.publish(request), (result, failure) -> failure == null
                ? new KioskCommand.PublishSucceeded(request.messageId())
                : new KioskCommand.PublishFailed(request, failure));
    }

    private Behavior<KioskCommand> onPublishFailed(KioskCommand.PublishFailed failure) {
        supervisor.tell(new SupervisorCommand.PublicationExhausted(
                state.deviceId(), failure.request().messageId(), failure.cause()));
        return this;
    }
}
