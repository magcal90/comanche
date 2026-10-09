package io.github.magcal90.comanche_device_simulator.simulation;

import java.time.Instant;

@FunctionalInterface
public interface SimulationClock {
    Instant atTick(long tick);
}
