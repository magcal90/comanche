package io.github.magcal90.comanche_device_simulator.simulation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class FixedStepSimulationClock implements SimulationClock {
    private final Instant start;
    private final Duration tickInterval;

    public FixedStepSimulationClock(Instant start, Duration tickInterval) {
        this.start = Objects.requireNonNull(start, "start");
        this.tickInterval = Objects.requireNonNull(tickInterval, "tickInterval");
    }

    @Override
    public Instant atTick(long tick) {
        if (tick < 0) throw new IllegalArgumentException("tick must not be negative");
        return start.plus(tickInterval.multipliedBy(tick));
    }
}
