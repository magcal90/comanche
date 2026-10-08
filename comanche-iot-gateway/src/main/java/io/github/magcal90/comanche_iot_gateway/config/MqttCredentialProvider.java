package io.github.magcal90.comanche_iot_gateway.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class MqttCredentialProvider {

	private static final String PASSWORD_PROPERTY = "comanche.gateway.mqtt.password";

	private final Environment environment;

	public MqttCredentialProvider(Environment environment) {
		this.environment = environment;
	}

	public char[] requiredPassword() {
		String password = environment.getProperty(PASSWORD_PROPERTY);
		if (!StringUtils.hasText(password)) {
			throw new IllegalStateException(PASSWORD_PROPERTY + " must be supplied when MQTT is enabled");
		}
		return password.toCharArray();
	}
}
