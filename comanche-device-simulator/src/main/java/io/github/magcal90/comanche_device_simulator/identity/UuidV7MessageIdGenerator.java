package io.github.magcal90.comanche_device_simulator.identity;

import com.github.f4b6a3.uuid.UuidCreator;

public final class UuidV7MessageIdGenerator implements MessageIdGenerator {
    @Override
    public String nextId(String deviceId) {
        return deviceId + '-' + UuidCreator.getTimeOrderedEpoch();
    }
}
