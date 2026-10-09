package io.github.magcal90.comanche_device_simulator.mqtt;

public final class MqttTopics {
    private MqttTopics() {}

    public static String forCategory(String deviceId, MessageCategory category) {
        String suffix = switch (category) {
            case TELEMETRY -> "telemetry";
            case STATUS -> "status";
            case DEVICE_EVENT -> "events";
        };
        return "comanche/devices/" + deviceId + '/' + suffix;
    }

    public static String status(String deviceId) {
        return forCategory(deviceId, MessageCategory.STATUS);
    }
}
