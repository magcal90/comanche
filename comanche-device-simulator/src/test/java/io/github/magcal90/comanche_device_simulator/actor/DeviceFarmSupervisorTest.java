package io.github.magcal90.comanche_device_simulator.actor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;

import io.github.magcal90.comanche_device_simulator.config.KioskConfiguration;
import io.github.magcal90.comanche_device_simulator.config.SimulatorConfiguration;
import io.github.magcal90.comanche_device_simulator.identity.MessageIdGenerator;
import io.github.magcal90.comanche_device_simulator.model.DeviceSnapshot;
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
    void publish_whenAttemptsFail_shouldPreserveLogicalIdentityAcrossRetries() {
        var publisher = new FakeMqttPublisher();
        publisher.failPublications();
        var supervisor = TEST_KIT.spawn(DeviceFarmSupervisor.create(configuration(1, 10, 10, 3),
                new FixedStepSimulationClock(START, Duration.ofSeconds(1)), publisher,
                fixedIds("hardcoded-event"), false));

        supervisor.tell(new SupervisorCommand.TickAll());

        TEST_KIT.<String>createTestProbe().awaitAssert(() -> {
            assertThat(publisher.requests()).hasSize(3);
            assertThat(publisher.requests()).extracting(request -> request.messageId()).containsOnly("hardcoded-event");
            assertThat(publisher.requests()).extracting(request -> request.attempt()).containsExactly(1, 2, 3);
            return null;
        });
    }

    private static SimulatorConfiguration configuration(int count, int telemetryTicks, int statusTicks, int attempts) {
        List<KioskConfiguration> kiosks = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> new KioskConfiguration("KIOSK-%03d".formatted(index), "SITE-1"))
                .toList();
        return new SimulatorConfiguration(42, START, Duration.ofSeconds(1), telemetryTicks, statusTicks,
                10, attempts, kiosks);
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
}
