package io.github.magcal90.comanche_device_simulator.config;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.github.magcal90.comanche_device_simulator.model.FaultType;

public final class SimulatorConfigurationLoader {
    private SimulatorConfigurationLoader() {}

    public static SimulatorConfiguration load() {
        return from(ConfigFactory.load().getConfig("comanche.simulator"));
    }

    static SimulatorConfiguration from(Config config) {
        int kioskCount = config.getInt("kiosk-count");
        if (kioskCount <= 0) throw new IllegalArgumentException("kiosk-count must be positive");
        String prefix = required(config.getString("device-id-prefix"), "device-id-prefix");
        String siteId = required(config.getString("site-id"), "site-id");
        int first = config.getInt("first-device-number");
        List<KioskConfiguration> kiosks = IntStream.range(0, kioskCount)
                .mapToObj(index -> new KioskConfiguration(prefix + "%03d".formatted(first + index), siteId))
                .toList();
        return new SimulatorConfiguration(
                config.getLong("random-seed"),
                Instant.parse(config.getString("start-time")),
                config.getDuration("tick-interval"),
                config.getInt("telemetry-interval-ticks"),
                config.getInt("status-interval-ticks"),
                config.getInt("publisher-buffer-capacity"),
                config.getInt("publication-max-attempts"),
                config.getDuration("publication-initial-backoff"),
                config.getDuration("publication-max-backoff"),
                new MqttConfiguration(
                        URI.create(config.getString("mqtt.server-uri")),
                        config.getDuration("mqtt.connection-timeout"),
                        config.getDuration("mqtt.keep-alive-interval")),
                kiosks,
                config.getConfigList("fault-scenarios").stream().map(SimulatorConfigurationLoader::faultScenario).toList());
    }

    private static FaultScenarioConfiguration faultScenario(Config config) {
        FaultType type;
        try {
            type = FaultType.valueOf(required(config.getString("fault-type"), "fault-type"));
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid fault-type: " + config.getString("fault-type"), exception);
        }
        double secondaryStep = optionalDouble(config, "secondary-step-per-tick");
        double secondaryMaximum = optionalDouble(config, "secondary-maximum");
        double secondaryRecovery = optionalDouble(config, "secondary-recovery-step-per-tick");
        return new FaultScenarioConfiguration(
                required(config.getString("scenario-id"), "scenario-id"),
                required(config.getString("device-id"), "device-id"),
                type,
                config.getLong("start-after-ticks"),
                config.getDouble("step-per-tick"),
                config.getDouble("maximum"),
                config.getDouble("recovery-step-per-tick"),
                config.getDouble("disabling-threshold"),
                secondaryStep, secondaryMaximum, secondaryRecovery);
    }

    private static double optionalDouble(Config config, String path) {
        return config.hasPath(path) ? config.getDouble(path) : 0;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
