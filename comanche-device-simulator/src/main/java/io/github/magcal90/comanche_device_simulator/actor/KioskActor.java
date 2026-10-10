package io.github.magcal90.comanche_device_simulator.actor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.magcal90.comanche_device_simulator.config.FaultScenarioConfiguration;
import io.github.magcal90.comanche_device_simulator.config.KioskConfiguration;
import io.github.magcal90.comanche_device_simulator.identity.MessageIdGenerator;
import io.github.magcal90.comanche_device_simulator.model.DeviceSnapshot;
import io.github.magcal90.comanche_device_simulator.model.FaultProfile;
import io.github.magcal90.comanche_device_simulator.model.FaultType;
import io.github.magcal90.comanche_device_simulator.model.KioskState;
import io.github.magcal90.comanche_device_simulator.model.PrinterStatus;
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
    private final List<FaultScenarioConfiguration> scenarios;
    private final Map<FaultType, FaultProfile> faultProfiles;
    private final Set<String> startedScenarios = new HashSet<>();
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
            int statusIntervalTicks,
            List<FaultScenarioConfiguration> scenarios) {
        super(context);
        this.clock = clock;
        this.publisher = publisher;
        this.ids = ids;
        this.supervisor = supervisor;
        this.telemetryIntervalTicks = telemetryIntervalTicks;
        this.statusIntervalTicks = statusIntervalTicks;
        this.scenarios = List.copyOf(scenarios);
        this.faultProfiles = scenarios.stream().collect(Collectors.toUnmodifiableMap(
                FaultScenarioConfiguration::faultType, FaultScenarioConfiguration::profile));
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
            int statusIntervalTicks,
            List<FaultScenarioConfiguration> scenarios) {
        return Behaviors.setup(context -> new KioskActor(context, config, seed, clock, publisher, ids,
                supervisor, telemetryIntervalTicks, statusIntervalTicks, scenarios));
    }

    @Override
    public Receive<KioskCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(KioskCommand.Tick.class, ignored -> onTick())
                .onMessage(KioskCommand.InjectFault.class, this::onInjectFault)
                .onMessage(KioskCommand.ClearFault.class, this::onClearFault)
                .onMessage(KioskCommand.GetSnapshot.class, this::onGetSnapshot)
                .onMessage(KioskCommand.PublishSucceeded.class, ignored -> this)
                .onMessage(KioskCommand.PublishFailed.class, this::onPublishFailed)
                .build();
    }

    private Behavior<KioskCommand> onTick() {
        scenarios.stream()
                .filter(scenario -> scenario.startAfterTicks() <= state.tick() + 1)
                .filter(scenario -> startedScenarios.add(scenario.scenarioId()))
                .forEach(scenario -> state = KioskSimulation.injectFault(state, scenario.profile()));
        KioskState previous = state;
        state = KioskSimulation.advance(state, clock);
        if (previous.operationalState() != state.operationalState()) {
            publish(MessageCategory.DEVICE_EVENT, Map.of(
                    "eventType", "OPERATIONAL_STATE_CHANGED",
                    "previousState", previous.operationalState().name(),
                    "newState", state.operationalState().name()));
        }
        if (previous.printerStatus() != PrinterStatus.ERROR && state.printerStatus() == PrinterStatus.ERROR) {
            publish(MessageCategory.DEVICE_EVENT, Map.of(
                    "eventType", "PRINTER_ERROR",
                    "errorCode", "PRINTER_THERMAL_WARNING",
                    "description", "Printer temperature reached the configured disabling threshold"));
        }
        if (previous.activeFaults().containsKey(FaultType.PRINTER_OVERHEATING)
                && !state.activeFaults().containsKey(FaultType.PRINTER_OVERHEATING)) {
            publish(MessageCategory.DEVICE_EVENT, Map.of(
                    "eventType", "PRINTER_RECOVERED",
                    "description", "Printer temperature and error rate returned to normal"));
        }
        if (state.tick() % telemetryIntervalTicks == 0) {
            publish(MessageCategory.TELEMETRY, Map.of("metrics", Map.of(
                    "system.cpu.utilization", state.cpuUtilization(),
                    "system.memory.utilization", state.memoryUtilization(),
                    "printer.temperature", state.printerTemperature(),
                    "printer.errorRate", state.printerErrorRate(),
                    "printer.paperLevel", state.printerPaperLevel(),
                    "printer.status", state.printerStatus().name())));
        }
        if (state.tick() % statusIntervalTicks == 0) {
            publish(MessageCategory.STATUS, Map.of(
                    "connectionStatus", "ONLINE",
                    "applicationStatus", "RUNNING",
                    "printerStatus", state.printerStatus().name()));
        }
        return this;
    }

    private Behavior<KioskCommand> onInjectFault(KioskCommand.InjectFault command) {
        FaultProfile profile = faultProfiles.get(command.faultType());
        if (profile == null) {
            getContext().getLog().warn("Ignoring unconfigured fault {} for {}", command.faultType(), state.deviceId());
            return this;
        }
        state = KioskSimulation.injectFault(state, profile);
        return this;
    }

    private Behavior<KioskCommand> onClearFault(KioskCommand.ClearFault command) {
        state = KioskSimulation.clearFault(state, command.faultType());
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
