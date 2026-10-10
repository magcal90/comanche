package io.github.magcal90.comanche_device_simulator.actor;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.magcal90.comanche_device_simulator.config.FaultScenarioConfiguration;
import io.github.magcal90.comanche_device_simulator.config.KioskConfiguration;
import io.github.magcal90.comanche_device_simulator.config.MqttConfiguration;
import io.github.magcal90.comanche_device_simulator.config.SimulatorConfiguration;
import io.github.magcal90.comanche_device_simulator.identity.MessageIdGenerator;
import io.github.magcal90.comanche_device_simulator.model.DeviceSnapshot;
import io.github.magcal90.comanche_device_simulator.model.FaultType;
import io.github.magcal90.comanche_device_simulator.mqtt.MessageCategory;
import io.github.magcal90.comanche_device_simulator.simulation.FixedStepSimulationClock;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceFarmSupervisorTest {
    private static final ActorTestKit TEST_KIT = ActorTestKit.create();
    private static final Instant START = Instant.parse("2026-10-08T00:00:00Z");

    @AfterAll
    static void shutdown() {
        TEST_KIT.shutdownTestKit();
    }

    @Test
    void create_whenTwoKiosksAreConfigured_shouldCreateIndependentActors() {
        var publisher = new FakeMqttPublisher();
        var supervisor = TEST_KIT.spawn(DeviceFarmSupervisor.create(configuration(2, 5, 30, 3),
                new FixedStepSimulationClock(START, Duration.ofSeconds(1)), publisher,
                fixedIds("event-1", "event-2"), false));
        TestProbe<Integer> countProbe = TEST_KIT.createTestProbe();
        supervisor.tell(new SupervisorCommand.GetDeviceCount(countProbe.ref()));
        assertThat(countProbe.receiveMessage()).isEqualTo(2);

        var first = device(supervisor, "KIOSK-001");
        var second = device(supervisor, "KIOSK-002");
        first.tell(new KioskCommand.Tick());

        assertThat(snapshot(first).state().tick()).isEqualTo(1);
        assertThat(snapshot(second).state().tick()).isZero();
    }

    @Test
    void tick_whenAllCategoriesAreDue_shouldUseUniqueIdsAcrossCategories() {
        var publisher = new FakeMqttPublisher();
        var supervisor = TEST_KIT.spawn(DeviceFarmSupervisor.create(configuration(1, 1, 1, 3),
                new FixedStepSimulationClock(START, Duration.ofSeconds(1)), publisher,
                fixedIds("hardcoded-event", "hardcoded-telemetry", "hardcoded-status"), false));

        supervisor.tell(new SupervisorCommand.TickAll());

        TEST_KIT.<String>createTestProbe().awaitAssert(() -> {
            assertThat(publisher.requests()).hasSize(3);
            assertThat(publisher.requests()).extracting(request -> request.messageId()).doesNotHaveDuplicates();
            assertThat(publisher.requests()).extracting(request -> request.category())
                    .containsExactlyInAnyOrder(MessageCategory.DEVICE_EVENT, MessageCategory.TELEMETRY, MessageCategory.STATUS);
            return null;
        });
    }

    @Test
    void publish_whenPublisherRejectsEvent_shouldLeaveStateIntactAndReportFailure() {
        var publisher = new FakeMqttPublisher();
        publisher.failPublications();
        var supervisor = TEST_KIT.spawn(DeviceFarmSupervisor.create(configuration(1, 10, 10, 3),
                new FixedStepSimulationClock(START, Duration.ofSeconds(1)), publisher,
                fixedIds("hardcoded-event"), false));

        supervisor.tell(new SupervisorCommand.TickAll());

        TEST_KIT.<String>createTestProbe().awaitAssert(() -> {
            assertThat(publisher.requests()).singleElement()
                    .extracting(request -> request.messageId()).isEqualTo("hardcoded-event");
            return null;
        });
        assertThat(snapshot(device(supervisor, "KIOSK-001")).state().tick()).isEqualTo(1);
    }

    @Test
    void tick_whenMqttPublicationFails_shouldContinueFaultProgression() {
        var publisher = new FakeMqttPublisher();
        publisher.failPublications();
        FaultScenarioConfiguration cpu = new FaultScenarioConfiguration("CPU-1", "KIOSK-001",
                FaultType.CPU_PRESSURE, 100, 10, 95, 10, 90, 0, 0, 0);
        var supervisor = TEST_KIT.spawn(DeviceFarmSupervisor.create(configuration(1, 1, 30, 3, List.of(cpu)),
                new FixedStepSimulationClock(START, Duration.ofSeconds(1)), publisher, sequentialIds(), false));
        var kiosk = device(supervisor, "KIOSK-001");

        kiosk.tell(new KioskCommand.InjectFault(FaultType.CPU_PRESSURE));
        kiosk.tell(new KioskCommand.Tick());
        double afterFirstTick = snapshot(kiosk).state().cpuUtilization();
        kiosk.tell(new KioskCommand.Tick());
        DeviceSnapshot afterSecondTick = snapshot(kiosk);

        assertThat(afterSecondTick.state().tick()).isEqualTo(2);
        assertThat(afterSecondTick.state().cpuUtilization()).isGreaterThan(afterFirstTick);
        assertThat(afterSecondTick.state().activeFaults()).containsKey(FaultType.CPU_PRESSURE);
    }

    @Test
    void injectFault_whenTargetingOneKiosk_shouldNotAffectAnotherKiosk() {
        var publisher = new FakeMqttPublisher();
        FaultScenarioConfiguration printer = new FaultScenarioConfiguration("PRINTER-1", "KIOSK-001",
                FaultType.PRINTER_OVERHEATING, 100, 8, 82, 10, 70, 0.05, 0.25, 0.08);
        var supervisor = TEST_KIT.spawn(DeviceFarmSupervisor.create(configuration(2, 1, 30, 3, List.of(printer)),
                new FixedStepSimulationClock(START, Duration.ofSeconds(1)), publisher, sequentialIds(), false));
        var first = device(supervisor, "KIOSK-001");
        var second = device(supervisor, "KIOSK-002");

        first.tell(new KioskCommand.InjectFault(FaultType.PRINTER_OVERHEATING));
        first.tell(new KioskCommand.Tick());
        second.tell(new KioskCommand.Tick());

        DeviceSnapshot firstSnapshot = snapshot(first);
        DeviceSnapshot secondSnapshot = snapshot(second);
        assertThat(firstSnapshot.state().activeFaults()).containsKey(FaultType.PRINTER_OVERHEATING);
        assertThat(secondSnapshot.state().activeFaults()).isEmpty();
        assertThat(firstSnapshot.state().printerTemperature())
                .isGreaterThan(secondSnapshot.state().printerTemperature());

        first.tell(new KioskCommand.ClearFault(FaultType.PRINTER_OVERHEATING));
        assertThat(snapshot(first).state().activeFaults().get(FaultType.PRINTER_OVERHEATING).phase().name())
                .isEqualTo("RECOVERING");
    }

    private static SimulatorConfiguration configuration(int count, int telemetryTicks, int statusTicks, int attempts) {
        return configuration(count, telemetryTicks, statusTicks, attempts, List.of());
    }

    private static SimulatorConfiguration configuration(int count, int telemetryTicks, int statusTicks, int attempts,
            List<FaultScenarioConfiguration> scenarios) {
        List<KioskConfiguration> kiosks = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> new KioskConfiguration("KIOSK-%03d".formatted(index), "SITE-1"))
                .toList();
        return new SimulatorConfiguration(42, START, Duration.ofSeconds(1), telemetryTicks, statusTicks,
                10, attempts, Duration.ofMillis(1), Duration.ofMillis(5),
                new MqttConfiguration(URI.create("tcp://localhost:1883"), Duration.ofSeconds(1), Duration.ofSeconds(5)),
                kiosks, scenarios);
    }

    private static org.apache.pekko.actor.typed.ActorRef<KioskCommand> device(
            org.apache.pekko.actor.typed.ActorRef<SupervisorCommand> supervisor, String id) {
        TestProbe<SupervisorCommand.DeviceRef> probe = TEST_KIT.createTestProbe();
        supervisor.tell(new SupervisorCommand.GetDevice(id, probe.ref()));
        return probe.receiveMessage().actorRef();
    }

    private static DeviceSnapshot snapshot(org.apache.pekko.actor.typed.ActorRef<KioskCommand> device) {
        TestProbe<DeviceSnapshot> probe = TEST_KIT.createTestProbe();
        device.tell(new KioskCommand.GetSnapshot(probe.ref()));
        return probe.receiveMessage();
    }

    private static MessageIdGenerator fixedIds(String... values) {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of(values));
        return ignored -> ids.removeFirst();
    }

    private static MessageIdGenerator sequentialIds() {
        AtomicInteger sequence = new AtomicInteger();
        return deviceId -> deviceId + "-test-" + sequence.incrementAndGet();
    }
}
