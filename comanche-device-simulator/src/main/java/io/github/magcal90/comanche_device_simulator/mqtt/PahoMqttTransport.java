package io.github.magcal90.comanche_device_simulator.mqtt;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import io.github.magcal90.comanche_device_simulator.config.KioskConfiguration;
import io.github.magcal90.comanche_device_simulator.config.MqttConfiguration;
import io.github.magcal90.comanche_device_simulator.config.MqttCredentials;
import io.github.magcal90.comanche_device_simulator.identity.MessageIdGenerator;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PahoMqttTransport implements MqttTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(PahoMqttTransport.class);
    private final Map<String, DeviceSession> sessions = new ConcurrentHashMap<>();

    public PahoMqttTransport(
            MqttConfiguration configuration,
            MqttCredentials credentials,
            List<KioskConfiguration> kiosks,
            MqttEnvelopeSerializer serializer,
            MessageIdGenerator ids,
            Clock clock) {
        kiosks.forEach(kiosk -> sessions.put(kiosk.deviceId(), createSession(
                configuration, credentials, kiosk.deviceId(), serializer, ids, clock)));
    }

    @Override
    public CompletionStage<Void> publish(EncodedPublication publication) {
        DeviceSession session = sessions.get(publication.request().deviceId());
        if (session == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("No MQTT session for " + publication.request().deviceId()));
        return session.publish(publication);
    }

    private static DeviceSession createSession(
            MqttConfiguration configuration,
            MqttCredentials credentials,
            String deviceId,
            MqttEnvelopeSerializer serializer,
            MessageIdGenerator ids,
            Clock clock) {
        try {
            String clientId = "comanche-device-" + deviceId;
            MqttAsyncClient client = new MqttAsyncClient(configuration.serverUri().toString(), clientId,
                    new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
            options.setCleanSession(false);
            options.setAutomaticReconnect(false);
            options.setUserName(credentials.username());
            options.setPassword(credentials.password());
            options.setConnectionTimeout(Math.toIntExact(configuration.connectionTimeout().toSeconds()));
            options.setKeepAliveInterval(Math.toIntExact(configuration.keepAliveInterval().toSeconds()));
            client.setCallback(new LoggingCallback(deviceId));
            return new DeviceSession(deviceId, client, options, serializer, ids, clock);
        }
        catch (MqttException exception) {
            throw new IllegalStateException("Cannot create MQTT client for " + deviceId, exception);
        }
    }

    @Override
    public void close() {
        sessions.values().forEach(DeviceSession::close);
    }

    void forceConnectionLossForTest(String deviceId) {
        DeviceSession session = sessions.get(deviceId);
        if (session == null) throw new IllegalArgumentException("No MQTT session for " + deviceId);
        session.forceConnectionLoss();
    }

    private static final class DeviceSession {
        private final String deviceId;
        private final MqttAsyncClient client;
        private final MqttConnectOptions options;
        private final MqttEnvelopeSerializer serializer;
        private final MessageIdGenerator ids;
        private final Clock clock;

        private DeviceSession(String deviceId, MqttAsyncClient client, MqttConnectOptions options,
                MqttEnvelopeSerializer serializer, MessageIdGenerator ids, Clock clock) {
            this.deviceId = deviceId;
            this.client = client;
            this.options = options;
            this.serializer = serializer;
            this.ids = ids;
            this.clock = clock;
        }

        CompletionStage<Void> publish(EncodedPublication publication) {
            CompletionStage<Void> connected = client.isConnected()
                    ? CompletableFuture.completedFuture(null) : connectAndAnnounce();
            return connected.thenCompose(ignored -> publishRaw(
                    publication.topic(), publication.payload(), publication.qos(), publication.retained()));
        }

        private CompletionStage<Void> connectAndAnnounce() {
            CompletableFuture<Void> result = new CompletableFuture<>();
            try {
                PublishRequest willRequest = new PublishRequest(ids.nextId(deviceId), deviceId, clock.instant(),
                        MessageCategory.STATUS, Map.of(
                                "connectionStatus", "OFFLINE",
                                "reason", "UNEXPECTED_DISCONNECT"), 1);
                options.setWill(MqttTopics.status(deviceId), serializer.serialize(willRequest), 1, true);
                client.connect(options, null, listener(() -> {
                    PublishRequest online = new PublishRequest(ids.nextId(deviceId), deviceId, clock.instant(),
                            MessageCategory.STATUS, Map.of(
                                    "connectionStatus", "ONLINE",
                                    "applicationStatus", "RUNNING"), 1);
                    publishRaw(MqttTopics.status(deviceId), serializer.serialize(online), 1, true)
                            .whenComplete((ignored, failure) -> complete(result, failure));
                }, result));
            }
            catch (MqttException exception) {
                result.completeExceptionally(exception);
            }
            return result;
        }

        private CompletionStage<Void> publishRaw(String topic, byte[] payload, int qos, boolean retained) {
            CompletableFuture<Void> result = new CompletableFuture<>();
            try {
                MqttMessage message = new MqttMessage(payload);
                message.setQos(qos);
                message.setRetained(retained);
                client.publish(topic, message, null, listener(() -> result.complete(null), result));
            }
            catch (MqttException exception) {
                result.completeExceptionally(exception);
            }
            return result;
        }

        private void close() {
            try {
                if (client.isConnected()) client.disconnectForcibly(1_000, 1_000, false);
                client.close();
            }
            catch (MqttException exception) {
                LOGGER.warn("Failed to close MQTT client for {}", deviceId, exception);
            }
        }

        private void forceConnectionLoss() {
            try {
                client.disconnectForcibly(0, 0, false);
            }
            catch (MqttException exception) {
                throw new IllegalStateException("Cannot force MQTT connection loss for " + deviceId, exception);
            }
        }
    }

    private static IMqttActionListener listener(Runnable success, CompletableFuture<Void> failureTarget) {
        return new IMqttActionListener() {
            @Override public void onSuccess(IMqttToken asyncActionToken) { success.run(); }
            @Override public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                failureTarget.completeExceptionally(exception);
            }
        };
    }

    private static void complete(CompletableFuture<Void> target, Throwable failure) {
        if (failure == null) target.complete(null); else target.completeExceptionally(failure);
    }

    private static final class LoggingCallback implements MqttCallbackExtended {
        private final String deviceId;
        private LoggingCallback(String deviceId) { this.deviceId = deviceId; }
        @Override public void connectionLost(Throwable cause) {
            LOGGER.warn("MQTT connection lost for {}", deviceId, cause);
        }
        @Override public void connectComplete(boolean reconnect, String serverURI) {
            LOGGER.info("MQTT connection established for {} at {}", deviceId, serverURI);
        }
        @Override public void messageArrived(String topic, MqttMessage message) {}
        @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {}
    }
}
