package io.github.magcal90.comanche_device_simulator.model;

import java.util.Objects;

public record ActiveFault(FaultProfile profile, FaultPhase phase, long progressionTicks) {
    public ActiveFault {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(phase, "phase");
        if (progressionTicks < 0) throw new IllegalArgumentException("progressionTicks must not be negative");
    }

    public static ActiveFault developing(FaultProfile profile) {
        return new ActiveFault(profile, FaultPhase.DEVELOPING, 0);
    }

    public ActiveFault recovering() {
        return new ActiveFault(profile, FaultPhase.RECOVERING, progressionTicks);
    }
}
