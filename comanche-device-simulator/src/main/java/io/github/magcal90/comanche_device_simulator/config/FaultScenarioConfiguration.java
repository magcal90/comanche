package io.github.magcal90.comanche_device_simulator.config;

import java.util.Objects;

import io.github.magcal90.comanche_device_simulator.model.FaultProfile;
import io.github.magcal90.comanche_device_simulator.model.FaultType;

public record FaultScenarioConfiguration(
        String scenarioId,
        String deviceId,
        FaultType faultType,
        long startAfterTicks,
        double primaryStep,
        double primaryMaximum,
        double primaryRecoveryStep,
        double disablingThreshold,
        double secondaryStep,
        double secondaryMaximum,
        double secondaryRecoveryStep) {

    public FaultScenarioConfiguration {
        if (scenarioId == null || scenarioId.isBlank()) throw new IllegalArgumentException("scenarioId must not be blank");
        if (deviceId == null || deviceId.isBlank()) throw new IllegalArgumentException("scenario deviceId must not be blank");
        Objects.requireNonNull(faultType, "faultType");
        if (startAfterTicks < 0) throw new IllegalArgumentException("startAfterTicks must not be negative");
        requirePositiveFinite(primaryStep, "primaryStep");
        requirePositiveFinite(primaryMaximum, "primaryMaximum");
        requirePositiveFinite(primaryRecoveryStep, "primaryRecoveryStep");
        requirePositiveFinite(disablingThreshold, "disablingThreshold");
        if (disablingThreshold > primaryMaximum) {
            throw new IllegalArgumentException("disablingThreshold must not exceed primaryMaximum");
        }
        if (faultType == FaultType.PRINTER_OVERHEATING) {
            requirePositiveFinite(secondaryStep, "secondaryStep");
            requirePositiveFinite(secondaryRecoveryStep, "secondaryRecoveryStep");
            if (!Double.isFinite(secondaryMaximum) || secondaryMaximum <= 0 || secondaryMaximum > 1) {
                throw new IllegalArgumentException("secondaryMaximum must be in (0, 1] for printer faults");
            }
        }
        else if (secondaryStep != 0 || secondaryMaximum != 0 || secondaryRecoveryStep != 0) {
            throw new IllegalArgumentException("secondary progression is only supported for printer faults");
        }
        if ((faultType == FaultType.CPU_PRESSURE || faultType == FaultType.MEMORY_PRESSURE)
                && primaryMaximum > 100) {
            throw new IllegalArgumentException("utilization primaryMaximum must not exceed 100");
        }
    }

    public FaultProfile profile() {
        return new FaultProfile(faultType, primaryStep, primaryMaximum, primaryRecoveryStep,
                disablingThreshold, secondaryStep, secondaryMaximum, secondaryRecoveryStep);
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be positive and finite");
    }
}
