package io.github.magcal90.comanche_device_simulator;

import io.github.magcal90.comanche_device_simulator.actor.DeviceFarmSupervisor;
import io.github.magcal90.comanche_device_simulator.actor.SupervisorCommand;
import io.github.magcal90.comanche_device_simulator.config.SimulatorConfiguration;
import io.github.magcal90.comanche_device_simulator.config.SimulatorConfigurationLoader;
import io.github.magcal90.comanche_device_simulator.identity.InProcessMessageIdGenerator;
import io.github.magcal90.comanche_device_simulator.mqtt.LoggingPublisher;
import io.github.magcal90.comanche_device_simulator.simulation.FixedStepSimulationClock;
import org.apache.pekko.actor.typed.ActorSystem;

public final class SimulatorApplication {
    private SimulatorApplication() {}

    public static void main(String[] args) {
        SimulatorConfiguration config = SimulatorConfigurationLoader.load();
        var clock = new FixedStepSimulationClock(config.startTime(), config.tickInterval());
        ActorSystem<SupervisorCommand> system = ActorSystem.create(
                DeviceFarmSupervisor.create(config, clock, new LoggingPublisher(),
                        new InProcessMessageIdGenerator(), true),
                "comanche-device-simulator");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> system.tell(new SupervisorCommand.Stop()),
                "simulator-shutdown"));
    }
}
