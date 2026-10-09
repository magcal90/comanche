package io.github.magcal90.comanche_device_simulator.simulation;

import java.nio.charset.StandardCharsets;

public final class SeedDeriver {
    private SeedDeriver() {}

    public static long forDevice(long globalSeed, String deviceId) {
        long hash = 0xcbf29ce484222325L;
        for (byte value : deviceId.getBytes(StandardCharsets.UTF_8)) {
            hash ^= Byte.toUnsignedInt(value);
            hash *= 0x100000001b3L;
        }
        return globalSeed ^ hash;
    }
}
