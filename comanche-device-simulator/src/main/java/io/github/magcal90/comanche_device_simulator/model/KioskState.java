package io.github.magcal90.comanche_device_simulator.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record KioskState(
        String deviceId,
        String siteId,
        long simulationSeed,
        long tick,
        Instant simulationTime,
        OperationalState operationalState,
        double cpuUtilization,
        double memoryUtilization,
        double printerTemperature,
        double printerErrorRate,
        double printerPaperLevel,
        PrinterStatus printerStatus,
        Map<FaultType, ActiveFault> activeFaults) {

    public KioskState {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(siteId, "siteId");
        Objects.requireNonNull(simulationTime, "simulationTime");
        Objects.requireNonNull(operationalState, "operationalState");
        Objects.requireNonNull(printerStatus, "printerStatus");
        activeFaults = Map.copyOf(Objects.requireNonNull(activeFaults, "activeFaults"));
    }
}
