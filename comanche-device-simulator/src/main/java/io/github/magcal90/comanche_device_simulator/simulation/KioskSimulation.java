package io.github.magcal90.comanche_device_simulator.simulation;

import io.github.magcal90.comanche_device_simulator.model.KioskState;
import io.github.magcal90.comanche_device_simulator.model.OperationalState;

public final class KioskSimulation {
    private KioskSimulation() {}

    public static KioskState initial(String deviceId, String siteId, long seed, SimulationClock clock) {
        return new KioskState(deviceId, siteId, seed, 0, clock.atTick(0), OperationalState.IDLE,
                bounded(seed, 0, 10, 22), bounded(seed, 0, 30, 48));
    }

    public static KioskState advance(KioskState state, SimulationClock clock) {
        long nextTick = state.tick() + 1;
        OperationalState nextState = switch (state.operationalState()) {
            case IDLE -> OperationalState.TAKING_ORDER;
            case TAKING_ORDER -> OperationalState.PROCESSING_PAYMENT;
            case PROCESSING_PAYMENT -> OperationalState.COMPLETING_ORDER;
            case COMPLETING_ORDER -> OperationalState.IDLE;
            case OUT_OF_SERVICE -> OperationalState.OUT_OF_SERVICE;
        };
        return new KioskState(state.deviceId(), state.siteId(), state.simulationSeed(), nextTick,
                clock.atTick(nextTick), nextState,
                bounded(state.simulationSeed(), nextTick, 12, 38),
                bounded(state.simulationSeed(), nextTick, 32, 58));
    }

    private static double bounded(long seed, long tick, int minimum, int maximum) {
        long mixed = mix(seed ^ (tick * 0x9E3779B97F4A7C15L));
        return minimum + Math.floorMod(mixed, maximum - minimum + 1);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
