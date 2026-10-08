package io.github.magcal90.comanche_iot_gateway.registry;

import io.github.magcal90.comanche_iot_gateway.config.GatewayIngestionProperties;
import io.github.magcal90.comanche_iot_gateway.port.DeviceRegistryPort;

import org.springframework.stereotype.Component;

@Component
public final class ConfigurationBackedDeviceRegistry implements DeviceRegistryPort {

	private final GatewayIngestionProperties properties;

	public ConfigurationBackedDeviceRegistry(GatewayIngestionProperties properties) {
		this.properties = properties;
	}

	@Override
	public boolean isRegistered(String deviceId) {
		return properties.registeredDeviceIds().contains(deviceId);
	}
}
