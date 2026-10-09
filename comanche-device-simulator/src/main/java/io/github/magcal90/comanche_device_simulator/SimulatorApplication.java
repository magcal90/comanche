package io.github.magcal90.comanche_device_simulator;

import java.time.Clock;

import io.github.magcal90.comanche_device_simulator.actor.DeviceFarmSupervisor;
import io.github.magcal90.comanche_device_simulator.actor.SupervisorCommand;
import io.github.magcal90.comanche_device_simulator.config.SimulatorConfiguration;
import io.github.magcal90.comanche_device_simulator.config.SimulatorConfigurationLoader;
import io.github.magcal90.comanche_device_simulator.config.MqttCredentials;
import io.github.magcal90.comanche_device_simulator.identity.UuidV7MessageIdGenerator;
import io.github.magcal90.comanche_device_simulator.mqtt.MqttEnvelopeSerializer;
import io.github.magcal90.comanche_device_simulator.mqtt.PahoMqttTransport;
import io.github.magcal90.comanche_device_simulator.mqtt.ReliableMqttPublisher;
import io.github.magcal90.comanche_device_simulator.simulation.FixedStepSimulationClock;
import org.apache.pekko.actor.typed.ActorSystem;
import tools.jackson.databind.ObjectMapper;

public final class SimulatorApplication {
    private SimulatorApplication() {}

    public static void main(String[] args) {
        SimulatorConfiguration config = SimulatorConfigurationLoader.load();
        var clock = new FixedStepSimulationClock(config.startTime(), config.tickInterval());
        var ids = new UuidV7MessageIdGenerator();
        var serializer = new MqttEnvelopeSerializer(new ObjectMapper());
        var transport = new PahoMqttTransport(config.mqtt(), MqttCredentials.fromEnvironment(),
                config.kiosks(), serializer, ids, Clock.systemUTC());
        var publisher = new ReliableMqttPublisher(transport, serializer, config.publisherBufferCapacity(),
                config.publicationMaxAttempts(), config.publicationInitialBackoff(), config.publicationMaxBackoff());
        ActorSystem<SupervisorCommand> system = ActorSystem.create(
                DeviceFarmSupervisor.create(config, clock, publisher, ids, true),
                "comanche-device-simulator");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> system.tell(new SupervisorCommand.Stop()),
                "simulator-shutdown"));
    }
}
