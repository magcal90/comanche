package io.github.magcal90.comanche_device_simulator.mqtt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import io.github.magcal90.comanche_device_simulator.actor.DeviceFarmSupervisor;
import io.github.magcal90.comanche_device_simulator.actor.SupervisorCommand;
import io.github.magcal90.comanche_device_simulator.config.FaultScenarioConfiguration;
import io.github.magcal90.comanche_device_simulator.config.KioskConfiguration;
import io.github.magcal90.comanche_device_simulator.config.MqttConfiguration;
import io.github.magcal90.comanche_device_simulator.config.MqttCredentials;
import io.github.magcal90.comanche_device_simulator.config.SimulatorConfiguration;
import io.github.magcal90.comanche_device_simulator.identity.MessageIdGenerator;
import io.github.magcal90.comanche_device_simulator.model.FaultType;
import io.github.magcal90.comanche_device_simulator.simulation.FixedStepSimulationClock;
import org.apache.pekko.actor.typed.ActorSystem;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PahoMqttPublisherIT {
    private static final String DEVICE_ID = "KIOSK-IT-001";
    private static final String USERNAME = "integration-test";
    private static final String PASSWORD = "integration-test";

    @Container
    static final GenericContainer<?> MQTT = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0.22"))
            .withCopyFileToContainer(MountableFile.forClasspathResource("mosquitto-auth-it.conf"),
                    "/mosquitto/config/mosquitto.conf")
            .withEnv("TEST_USERNAME", USERNAME)
            .withEnv("TEST_PASSWORD", PASSWORD)
            .withCreateContainerCmdModifier(command -> command.withEntrypoint("/bin/sh").withCmd("-ec",
                    "mosquitto_passwd -b -c /mosquitto/data/passwords \"$TEST_USERNAME\" \"$TEST_PASSWORD\"; "
                            + "chown mosquitto:mosquitto /mosquitto/data/passwords; "
                            + "exec /usr/sbin/mosquitto -c /mosquitto/config/mosquitto.conf"))
            .withExposedPorts(1883);

    @Test
    void publish_shouldUseSpecTopicsQosRetentionLwtAndReconnect() throws Exception {
        String broker = "tcp://" + MQTT.getHost() + ':' + MQTT.getMappedPort(1883);
        List<Received> received = new CopyOnWriteArrayList<>();
        MqttClient subscriber = subscriber(broker, "simulator-it-subscriber", received);
        try {
            subscriber.subscribe("comanche/devices/" + DEVICE_ID + "/#", 1);
            var mapper = new ObjectMapper();
            var serializer = new MqttEnvelopeSerializer(mapper);
            AtomicInteger sequence = new AtomicInteger();
            MessageIdGenerator ids = deviceId -> deviceId + "-fixed-" + sequence.incrementAndGet();
            var mqttConfig = new MqttConfiguration(URI.create(broker), Duration.ofSeconds(3), Duration.ofSeconds(5));
            var transport = new PahoMqttTransport(mqttConfig,
                    MqttCredentials.of(USERNAME, PASSWORD.toCharArray()),
                    List.of(new KioskConfiguration(DEVICE_ID, "SITE-IT")), serializer, ids,
                    Clock.fixed(Instant.parse("2026-10-08T04:00:00Z"), ZoneOffset.UTC));
            try (var publisher = new ReliableMqttPublisher(transport, serializer, 10, 3,
                    Duration.ofMillis(20), Duration.ofMillis(100))) {
                publisher.publish(request("telemetry-id", MessageCategory.TELEMETRY,
                        Map.of("metrics", Map.of("system.cpu.utilization", 25.0))))
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                publisher.publish(request("status-id", MessageCategory.STATUS,
                        Map.of("connectionStatus", "ONLINE", "applicationStatus", "RUNNING")))
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                publisher.publish(request("event-id", MessageCategory.DEVICE_EVENT,
                        Map.of("eventType", "OPERATIONAL_STATE_CHANGED", "previousState", "IDLE",
                                "newState", "TAKING_ORDER")))
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);

                await(received, item -> item.messageId(mapper).equals("telemetry-id"));
                await(received, item -> item.messageId(mapper).equals("status-id"));
                await(received, item -> item.messageId(mapper).equals("event-id"));
                assertPublication(received, mapper, "telemetry-id", "telemetry", "TELEMETRY", false);
                assertPublication(received, mapper, "status-id", "status", "STATUS", false);
                assertPublication(received, mapper, "event-id", "events", "DEVICE_EVENT", false);

                MqttClient lateSubscriber = subscriber(broker, "simulator-it-late", received);
                try {
                    lateSubscriber.subscribe(MqttTopics.status(DEVICE_ID), 1);
                    Received retained = await(received, item -> item.retained && item.messageId(mapper).equals("status-id"));
                    assertThat(retained.qos).isEqualTo(1);
                }
                finally {
                    lateSubscriber.disconnect();
                    lateSubscriber.close();
                }

                transport.forceConnectionLossForTest(DEVICE_ID);
                Received offline = await(received, item -> "OFFLINE".equals(item.connectionStatus(mapper)));
                assertThat(offline.retained).isFalse();
                assertThat(offline.qos).isEqualTo(1);
                assertThat(offline.root(mapper).get("payload").get("reason").asText())
                        .isEqualTo("UNEXPECTED_DISCONNECT");

                MqttClient offlineLateSubscriber = subscriber(broker, "simulator-it-offline-late", received);
                try {
                    offlineLateSubscriber.subscribe(MqttTopics.status(DEVICE_ID), 1);
                    Received retainedOffline = await(received, item -> item.retained
                            && "OFFLINE".equals(item.connectionStatus(mapper)));
                    assertThat(retainedOffline.qos).isEqualTo(1);
                    assertThat(retainedOffline.messageId(mapper)).isEqualTo(offline.messageId(mapper));
                }
                finally {
                    offlineLateSubscriber.disconnect();
                    offlineLateSubscriber.close();
                }

                publisher.publish(request("after-reconnect", MessageCategory.TELEMETRY,
                        Map.of("metrics", Map.of("system.memory.utilization", 44.0))))
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                await(received, item -> item.messageId(mapper).equals("after-reconnect"));
                await(received, item -> "ONLINE".equals(item.connectionStatus(mapper))
                        && received.indexOf(item) > received.indexOf(offline));
            }
        }
        finally {
            subscriber.disconnect();
            subscriber.close();
        }
    }

    @Test
    void faultProgression_shouldPublishGatewayValidTelemetryAndExistingPrinterEvents() throws Exception {
        String broker = "tcp://" + MQTT.getHost() + ':' + MQTT.getMappedPort(1883);
        List<Received> received = new CopyOnWriteArrayList<>();
        MqttClient subscriber = subscriber(broker, "simulator-fault-it-subscriber", received);
        ActorSystem<SupervisorCommand> system = null;
        try {
            subscriber.subscribe("comanche/devices/" + DEVICE_ID + "/#", 1);
            var mapper = new ObjectMapper();
            var serializer = new MqttEnvelopeSerializer(mapper);
            AtomicInteger sequence = new AtomicInteger();
            MessageIdGenerator ids = deviceId -> deviceId + "-fault-it-" + sequence.incrementAndGet();
            var mqttConfig = new MqttConfiguration(URI.create(broker), Duration.ofSeconds(3), Duration.ofSeconds(5));
            var kiosk = new KioskConfiguration(DEVICE_ID, "SITE-IT");
            var scenario = new FaultScenarioConfiguration("PRINTER-IT", DEVICE_ID,
                    FaultType.PRINTER_OVERHEATING, 1, 8, 82, 10, 60, 0.05, 0.25, 0.08);
            var simulator = new SimulatorConfiguration(42, Instant.parse("2026-10-08T04:00:00Z"),
                    Duration.ofSeconds(1), 1, 30, 20, 3, Duration.ofMillis(20), Duration.ofMillis(100),
                    mqttConfig, List.of(kiosk), List.of(scenario));
            var transport = new PahoMqttTransport(mqttConfig, MqttCredentials.of(USERNAME, PASSWORD.toCharArray()),
                    List.of(kiosk), serializer, ids, Clock.fixed(Instant.parse("2026-10-08T04:00:00Z"), ZoneOffset.UTC));
            var publisher = new ReliableMqttPublisher(transport, serializer, 20, 3,
                    Duration.ofMillis(20), Duration.ofMillis(100));
            system = ActorSystem.create(DeviceFarmSupervisor.create(simulator,
                    new FixedStepSimulationClock(simulator.startTime(), simulator.tickInterval()),
                    publisher, ids, false), "fault-mqtt-it");

            for (int tick = 0; tick < 4; tick++) system.tell(new SupervisorCommand.TickAll());

            Received telemetry = await(received, item -> item.topic.endsWith("/telemetry")
                    && item.root(mapper).path("payload").path("metrics").path("printer.temperature").asDouble() >= 60);
            JsonNode metrics = telemetry.root(mapper).path("payload").path("metrics");
            assertThat(metrics.path("printer.errorRate").asDouble()).isBetween(0.0, 1.0);
            assertThat(metrics.path("system.cpu.utilization").asDouble()).isBetween(0.0, 100.0);
            assertThat(metrics.path("system.memory.utilization").asDouble()).isBetween(0.0, 100.0);
            assertThat(telemetry.qos).isEqualTo(1);
            assertThat(telemetry.retained).isFalse();

            Received printerError = await(received, item -> "PRINTER_ERROR".equals(item.eventType(mapper)));
            assertThat(printerError.topic).isEqualTo(MqttTopics.events(DEVICE_ID));
            assertThat(printerError.root(mapper).path("payload").path("errorCode").asText())
                    .isEqualTo("PRINTER_THERMAL_WARNING");
            await(received, item -> "OPERATIONAL_STATE_CHANGED".equals(item.eventType(mapper))
                    && "OUT_OF_SERVICE".equals(item.root(mapper).path("payload").path("newState").asText()));

            system.tell(new SupervisorCommand.ClearFault(DEVICE_ID, FaultType.PRINTER_OVERHEATING));
            for (int tick = 0; tick < 10; tick++) system.tell(new SupervisorCommand.TickAll());
            Received recovered = await(received, item -> "PRINTER_RECOVERED".equals(item.eventType(mapper)));
            assertThat(recovered.topic).isEqualTo(MqttTopics.events(DEVICE_ID));
            assertThat(recovered.root(mapper).path("messageType").asText()).isEqualTo("DEVICE_EVENT");
        }
        finally {
            if (system != null) {
                system.tell(new SupervisorCommand.Stop());
                system.getWhenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
            subscriber.disconnect();
            subscriber.close();
        }
    }

    private static PublishRequest request(String id, MessageCategory category, Map<String, Object> payload) {
        return new PublishRequest(id, DEVICE_ID, Instant.parse("2026-10-08T04:00:01Z"), category, payload, 1);
    }

    private static MqttClient subscriber(String broker, String clientId, List<Received> received) throws Exception {
        MqttClient client = new MqttClient(broker, clientId);
        client.setCallback(new MqttCallback() {
            @Override public void connectionLost(Throwable cause) {}
            @Override public void messageArrived(String topic, MqttMessage message) {
                received.add(new Received(topic, new String(message.getPayload(), StandardCharsets.UTF_8),
                        message.getQos(), message.isRetained()));
            }
            @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {}
        });
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setUserName(USERNAME);
        options.setPassword(PASSWORD.toCharArray());
        client.connect(options);
        return client;
    }

    private static void assertPublication(List<Received> received, ObjectMapper mapper, String id,
            String suffix, String type, boolean retained) {
        Received item = received.stream().filter(candidate -> candidate.messageId(mapper).equals(id)).findFirst().orElseThrow();
        assertThat(item.topic).isEqualTo("comanche/devices/" + DEVICE_ID + '/' + suffix);
        assertThat(item.qos).isEqualTo(1);
        assertThat(item.retained).isEqualTo(retained);
        assertThat(item.root(mapper).get("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(item.root(mapper).get("deviceId").asText()).isEqualTo(DEVICE_ID);
        assertThat(item.root(mapper).get("messageType").asText()).isEqualTo(type);
    }

    private static Received await(List<Received> received, Predicate<Received> predicate) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            for (Received item : received) if (predicate.test(item)) return item;
            Thread.sleep(25);
        }
        throw new AssertionError("Expected MQTT publication was not received; received=" + received);
    }

    private record Received(String topic, String json, int qos, boolean retained) {
        JsonNode root(ObjectMapper mapper) {
            try { return mapper.readTree(json); }
            catch (Exception exception) { throw new AssertionError(exception); }
        }
        String messageId(ObjectMapper mapper) { return root(mapper).path("messageId").asText(); }
        String connectionStatus(ObjectMapper mapper) {
            return root(mapper).path("payload").path("connectionStatus").asText();
        }
        String eventType(ObjectMapper mapper) {
            return root(mapper).path("payload").path("eventType").asText();
        }
    }
}
