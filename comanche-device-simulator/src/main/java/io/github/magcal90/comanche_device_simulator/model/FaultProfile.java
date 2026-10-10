package io.github.magcal90.comanche_device_simulator.model;

public record FaultProfile(
        FaultType type,
        double primaryStep,
        double primaryMaximum,
        double primaryRecoveryStep,
        double disablingThreshold,
        double secondaryStep,
        double secondaryMaximum,
        double secondaryRecoveryStep) {
}
