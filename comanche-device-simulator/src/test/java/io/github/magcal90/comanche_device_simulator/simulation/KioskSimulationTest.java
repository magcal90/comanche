package io.github.magcal90.comanche_device_simulator.simulation;

import java.time.Duration;
import java.time.Instant;

import io.github.magcal90.comanche_device_simulator.model.OperationalState;
import io.github.magcal90.comanche_device_simulator.model.FaultProfile;
import io.github.magcal90.comanche_device_simulator.model.FaultType;
import io.github.magcal90.comanche_device_simulator.model.PrinterStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KioskSimulationTest {
    private static final SimulationClock CLOCK =
            new FixedStepSimulationClock(Instant.parse("2026-10-08T00:00:00Z"), Duration.ofSeconds(1));

    @Test
    void advance_whenFourTicksOccur_shouldCompleteNormalOperationalCycle() {
        var state = KioskSimulation.initial("KIOSK-1", "SITE-1", 42, CLOCK);

        state = KioskSimulation.advance(state, CLOCK);
        assertThat(state.operationalState()).isEqualTo(OperationalState.TAKING_ORDER);
        state = KioskSimulation.advance(state, CLOCK);
        assertThat(state.operationalState()).isEqualTo(OperationalState.PROCESSING_PAYMENT);
        state = KioskSimulation.advance(state, CLOCK);
        assertThat(state.operationalState()).isEqualTo(OperationalState.COMPLETING_ORDER);
        state = KioskSimulation.advance(state, CLOCK);

        assertThat(state.operationalState()).isEqualTo(OperationalState.IDLE);
        assertThat(state.simulationTime()).isEqualTo(Instant.parse("2026-10-08T00:00:04Z"));
    }

    @Test
    void advance_whenInputsAreEqual_shouldProduceEquivalentSequences() {
        var first = KioskSimulation.initial("KIOSK-1", "SITE-1", 99, CLOCK);
        var second = KioskSimulation.initial("KIOSK-1", "SITE-1", 99, CLOCK);

        for (int tick = 0; tick < 20; tick++) {
            first = KioskSimulation.advance(first, CLOCK);
            second = KioskSimulation.advance(second, CLOCK);
            assertThat(first).isEqualTo(second);
        }
    }

    @Test
    void advance_shouldKeepGatewayValidatedUtilizationMetricsInRange() {
        var state = KioskSimulation.initial("KIOSK-1", "SITE-1", 99, CLOCK);
        for (int tick = 0; tick < 100; tick++) {
            state = KioskSimulation.advance(state, CLOCK);
            assertThat(state.cpuUtilization()).isBetween(0.0, 100.0);
            assertThat(state.memoryUtilization()).isBetween(0.0, 100.0);
        }
    }

    @Test
    void advance_whenPrinterFaultDevelopsAndClears_shouldProgressAndRecoverDeterministically() {
        FaultProfile profile = new FaultProfile(FaultType.PRINTER_OVERHEATING,
                8, 82, 10, 70, 0.05, 0.25, 0.08);
        var state = KioskSimulation.injectFault(
                KioskSimulation.initial("KIOSK-1", "SITE-1", 42, CLOCK), profile);
        double initialTemperature = state.printerTemperature();
        double initialErrorRate = state.printerErrorRate();

        state = KioskSimulation.advance(state, CLOCK);
        assertThat(state.printerTemperature()).isGreaterThan(initialTemperature);
        assertThat(state.printerErrorRate()).isGreaterThan(initialErrorRate);
        assertThat(state.printerStatus()).isEqualTo(PrinterStatus.DEGRADED);
        state = KioskSimulation.advance(state, CLOCK);
        state = KioskSimulation.advance(state, CLOCK);

        assertThat(state.operationalState()).isEqualTo(OperationalState.OUT_OF_SERVICE);
        assertThat(state.printerStatus()).isEqualTo(PrinterStatus.ERROR);

        state = KioskSimulation.clearFault(state, FaultType.PRINTER_OVERHEATING);
        for (int tick = 0; tick < 10 && state.activeFaults().containsKey(FaultType.PRINTER_OVERHEATING); tick++) {
            state = KioskSimulation.advance(state, CLOCK);
        }
        assertThat(state.activeFaults()).doesNotContainKey(FaultType.PRINTER_OVERHEATING);
        assertThat(state.printerStatus()).isEqualTo(PrinterStatus.NORMAL);
        assertThat(state.operationalState()).isNotEqualTo(OperationalState.OUT_OF_SERVICE);
    }

    @Test
    void advance_whenCpuAndMemoryPressureAreActive_shouldProgressIndependentlyWithinContractRanges() {
        var state = KioskSimulation.initial("KIOSK-1", "SITE-1", 91, CLOCK);
        state = KioskSimulation.injectFault(state,
                new FaultProfile(FaultType.CPU_PRESSURE, 7, 98, 11, 95, 0, 0, 0));
        state = KioskSimulation.injectFault(state,
                new FaultProfile(FaultType.MEMORY_PRESSURE, 5, 96, 9, 94, 0, 0, 0));
        double cpu = state.cpuUtilization();
        double memory = state.memoryUtilization();

        state = KioskSimulation.advance(state, CLOCK);
        assertThat(state.cpuUtilization()).isEqualTo(cpu + 7);
        assertThat(state.memoryUtilization()).isEqualTo(memory + 5);
        for (int tick = 0; tick < 20; tick++) state = KioskSimulation.advance(state, CLOCK);

        assertThat(state.cpuUtilization()).isEqualTo(98);
        assertThat(state.memoryUtilization()).isEqualTo(96);
        assertThat(state.operationalState()).isEqualTo(OperationalState.OUT_OF_SERVICE);
    }
}
