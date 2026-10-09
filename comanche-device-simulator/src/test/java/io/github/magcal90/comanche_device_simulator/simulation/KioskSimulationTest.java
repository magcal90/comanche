package io.github.magcal90.comanche_device_simulator.simulation;

import java.time.Duration;
import java.time.Instant;

import io.github.magcal90.comanche_device_simulator.model.OperationalState;
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
}
