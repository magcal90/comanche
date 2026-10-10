package io.github.magcal90.comanche_device_simulator.simulation;

import java.util.EnumMap;
import java.util.Map;

import io.github.magcal90.comanche_device_simulator.model.ActiveFault;
import io.github.magcal90.comanche_device_simulator.model.FaultPhase;
import io.github.magcal90.comanche_device_simulator.model.FaultProfile;
import io.github.magcal90.comanche_device_simulator.model.FaultType;
import io.github.magcal90.comanche_device_simulator.model.KioskState;
import io.github.magcal90.comanche_device_simulator.model.OperationalState;
import io.github.magcal90.comanche_device_simulator.model.PrinterStatus;

public final class KioskSimulation {
    private static final double NORMAL_PRINTER_ERROR_RATE = 0.01;
    private KioskSimulation() {}

    public static KioskState initial(String deviceId, String siteId, long seed, SimulationClock clock) {
        return new KioskState(deviceId, siteId, seed, 0, clock.atTick(0), OperationalState.IDLE,
                bounded(seed, 0, 10, 22), bounded(seed, 0, 30, 48),
                bounded(seed ^ 17, 0, 45, 52), NORMAL_PRINTER_ERROR_RATE, 100.0,
                PrinterStatus.NORMAL, Map.of());
    }

    public static KioskState injectFault(KioskState state, FaultProfile profile) {
        var faults = new EnumMap<FaultType, ActiveFault>(FaultType.class);
        faults.putAll(state.activeFaults());
        faults.putIfAbsent(profile.type(), ActiveFault.developing(profile));
        return copy(state, state.operationalState(), state.cpuUtilization(), state.memoryUtilization(),
                state.printerTemperature(), state.printerErrorRate(), state.printerPaperLevel(),
                state.printerStatus(), faults);
    }

    public static KioskState clearFault(KioskState state, FaultType type) {
        ActiveFault active = state.activeFaults().get(type);
        if (active == null || active.phase() == FaultPhase.RECOVERING) return state;
        var faults = new EnumMap<FaultType, ActiveFault>(FaultType.class);
        faults.putAll(state.activeFaults());
        faults.put(type, active.recovering());
        return copy(state, state.operationalState(), state.cpuUtilization(), state.memoryUtilization(),
                state.printerTemperature(), state.printerErrorRate(), state.printerPaperLevel(),
                state.printerStatus(), faults);
    }

    public static KioskState advance(KioskState state, SimulationClock clock) {
        long nextTick = state.tick() + 1;
        double normalCpu = bounded(state.simulationSeed(), nextTick, 12, 38);
        double normalMemory = bounded(state.simulationSeed(), nextTick, 32, 58);
        double normalPrinterTemperature = bounded(state.simulationSeed() ^ 17, nextTick, 45, 52);
        double cpu = state.activeFaults().containsKey(FaultType.CPU_PRESSURE) ? state.cpuUtilization() : normalCpu;
        double memory = state.activeFaults().containsKey(FaultType.MEMORY_PRESSURE) ? state.memoryUtilization() : normalMemory;
        double printerTemperature = state.activeFaults().containsKey(FaultType.PRINTER_OVERHEATING)
                ? state.printerTemperature() : normalPrinterTemperature;
        double printerErrorRate = state.activeFaults().containsKey(FaultType.PRINTER_OVERHEATING)
                ? state.printerErrorRate() : NORMAL_PRINTER_ERROR_RATE;
        var nextFaults = new EnumMap<FaultType, ActiveFault>(FaultType.class);

        for (ActiveFault fault : state.activeFaults().values()) {
            FaultProfile profile = fault.profile();
            if (fault.phase() == FaultPhase.RECOVERING) {
                boolean recovered;
                switch (profile.type()) {
                    case CPU_PRESSURE -> {
                        cpu = Math.max(normalCpu, cpu - profile.primaryRecoveryStep());
                        recovered = cpu <= normalCpu;
                    }
                    case MEMORY_PRESSURE -> {
                        memory = Math.max(normalMemory, memory - profile.primaryRecoveryStep());
                        recovered = memory <= normalMemory;
                    }
                    case PRINTER_OVERHEATING -> {
                        printerTemperature = Math.max(normalPrinterTemperature,
                                printerTemperature - profile.primaryRecoveryStep());
                        printerErrorRate = Math.max(NORMAL_PRINTER_ERROR_RATE,
                                printerErrorRate - profile.secondaryRecoveryStep());
                        recovered = printerTemperature <= normalPrinterTemperature
                                && printerErrorRate <= NORMAL_PRINTER_ERROR_RATE;
                    }
                    default -> throw new IllegalStateException("Unsupported fault type " + profile.type());
                }
                if (!recovered) nextFaults.put(profile.type(), fault);
            }
            else {
                boolean maximumReached;
                switch (profile.type()) {
                    case CPU_PRESSURE -> {
                        cpu = Math.min(profile.primaryMaximum(), cpu + profile.primaryStep());
                        maximumReached = cpu >= profile.primaryMaximum();
                    }
                    case MEMORY_PRESSURE -> {
                        memory = Math.min(profile.primaryMaximum(), memory + profile.primaryStep());
                        maximumReached = memory >= profile.primaryMaximum();
                    }
                    case PRINTER_OVERHEATING -> {
                        printerTemperature = Math.min(profile.primaryMaximum(),
                                printerTemperature + profile.primaryStep());
                        printerErrorRate = Math.min(profile.secondaryMaximum(),
                                printerErrorRate + profile.secondaryStep());
                        maximumReached = printerTemperature >= profile.primaryMaximum()
                                && printerErrorRate >= profile.secondaryMaximum();
                    }
                    default -> throw new IllegalStateException("Unsupported fault type " + profile.type());
                }
                nextFaults.put(profile.type(), new ActiveFault(profile,
                        maximumReached ? FaultPhase.ACTIVE : FaultPhase.DEVELOPING,
                        fault.progressionTicks() + 1));
            }
        }

        final double evaluatedCpu = cpu;
        final double evaluatedMemory = memory;
        final double evaluatedPrinterTemperature = printerTemperature;
        boolean disabling = nextFaults.values().stream()
                .anyMatch(fault -> primaryMetric(fault.profile().type(), evaluatedCpu, evaluatedMemory,
                        evaluatedPrinterTemperature) >= fault.profile().disablingThreshold());
        OperationalState operationalState = nextOperationalState(state.operationalState(), disabling);
        PrinterStatus printerStatus = printerStatus(nextFaults, printerTemperature);
        double paper = Math.max(0, state.printerPaperLevel()
                - (state.operationalState() == OperationalState.COMPLETING_ORDER ? 0.5 : 0));

        return new KioskState(state.deviceId(), state.siteId(), state.simulationSeed(), nextTick,
                clock.atTick(nextTick), operationalState, clamp(cpu, 0, 100), clamp(memory, 0, 100),
                printerTemperature, clamp(printerErrorRate, 0, 1), paper, printerStatus, nextFaults);
    }

    private static OperationalState nextOperationalState(OperationalState current, boolean disabling) {
        if (disabling) return OperationalState.OUT_OF_SERVICE;
        if (current == OperationalState.OUT_OF_SERVICE) return OperationalState.IDLE;
        return switch (current) {
            case IDLE -> OperationalState.TAKING_ORDER;
            case TAKING_ORDER -> OperationalState.PROCESSING_PAYMENT;
            case PROCESSING_PAYMENT -> OperationalState.COMPLETING_ORDER;
            case COMPLETING_ORDER -> OperationalState.IDLE;
            case OUT_OF_SERVICE -> OperationalState.IDLE;
        };
    }

    private static PrinterStatus printerStatus(Map<FaultType, ActiveFault> faults, double temperature) {
        ActiveFault printer = faults.get(FaultType.PRINTER_OVERHEATING);
        if (printer == null) return PrinterStatus.NORMAL;
        if (temperature >= printer.profile().disablingThreshold()) return PrinterStatus.ERROR;
        return PrinterStatus.DEGRADED;
    }

    private static double primaryMetric(FaultType type, double cpu, double memory, double printerTemperature) {
        return switch (type) {
            case CPU_PRESSURE -> cpu;
            case MEMORY_PRESSURE -> memory;
            case PRINTER_OVERHEATING -> printerTemperature;
        };
    }

    private static KioskState copy(KioskState state, OperationalState operationalState, double cpu, double memory,
            double printerTemperature, double printerErrorRate, double paper, PrinterStatus printerStatus,
            Map<FaultType, ActiveFault> faults) {
        return new KioskState(state.deviceId(), state.siteId(), state.simulationSeed(), state.tick(),
                state.simulationTime(), operationalState, cpu, memory, printerTemperature,
                printerErrorRate, paper, printerStatus, faults);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
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
