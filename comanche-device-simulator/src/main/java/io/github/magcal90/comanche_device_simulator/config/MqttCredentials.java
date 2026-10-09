package io.github.magcal90.comanche_device_simulator.config;

import java.util.Map;

public final class MqttCredentials {
    static final String USERNAME_ENV = "COMANCHE_SIMULATOR_MQTT_USERNAME";
    static final String PASSWORD_ENV = "COMANCHE_SIMULATOR_MQTT_PASSWORD";

    private final String username;
    private final char[] password;

    private MqttCredentials(String username, char[] password) {
        this.username = username;
        this.password = password.clone();
    }

    public static MqttCredentials fromEnvironment() {
        return from(System.getenv());
    }

    public static MqttCredentials of(String username, char[] password) {
        return new MqttCredentials(required(username, "username"),
                required(password == null ? null : new String(password), "password").toCharArray());
    }

    static MqttCredentials from(Map<String, String> environment) {
        String username = required(environment.get(USERNAME_ENV), USERNAME_ENV);
        String password = required(environment.get(PASSWORD_ENV), PASSWORD_ENV);
        return new MqttCredentials(username, password.toCharArray());
    }

    public String username() { return username; }
    public char[] password() { return password.clone(); }

    @Override
    public String toString() {
        return "MqttCredentials[username=" + username + ", password=<redacted>]";
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be set");
        return value;
    }
}
