package io.github.magcal90.comanche_device_simulator.config;

public record KioskConfiguration(String deviceId, String siteId) {
    public KioskConfiguration {
        if (deviceId == null || deviceId.isBlank()) throw new IllegalArgumentException("deviceId must not be blank");
        if (siteId == null || siteId.isBlank()) throw new IllegalArgumentException("siteId must not be blank");
    }
}
