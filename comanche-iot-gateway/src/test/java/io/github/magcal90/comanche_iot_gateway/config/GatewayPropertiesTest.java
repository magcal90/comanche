package io.github.magcal90.comanche_iot_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesConfiguration.class);

	@Test
	void bind_whenValuesAreValid_shouldConvertDurationsAndDataSize() {
		contextRunner.withPropertyValues(
				"comanche.gateway.mqtt.enabled=true",
				"comanche.gateway.mqtt.server-uri=tcp://localhost:1883",
				"comanche.gateway.mqtt.client-id=gateway-1",
				"comanche.gateway.mqtt.connection-timeout=2s",
				"comanche.gateway.mqtt.keep-alive-interval=30s",
				"comanche.gateway.mqtt.max-reconnect-delay=5s",
				"comanche.gateway.mqtt.completion-timeout=3s",
				"comanche.gateway.ingestion.registered-device-ids=KIOSK-1,POS-1",
				"comanche.gateway.ingestion.max-message-size=32KB",
				"comanche.gateway.ingestion.kafka-ack-timeout=4s")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(GatewayMqttProperties.class).connectionTimeout()).hasSeconds(2);
					assertThat(context.getBean(GatewayIngestionProperties.class).maxMessageSize().toKilobytes()).isEqualTo(32);
				});
	}

	@Test
	void bind_whenDurationIsNegative_shouldFailStartup() {
		contextRunner.withPropertyValues(
				"comanche.gateway.mqtt.enabled=true",
				"comanche.gateway.mqtt.server-uri=tcp://localhost:1883",
				"comanche.gateway.mqtt.client-id=gateway-1",
				"comanche.gateway.mqtt.connection-timeout=-1s",
				"comanche.gateway.mqtt.keep-alive-interval=30s",
				"comanche.gateway.mqtt.max-reconnect-delay=5s",
				"comanche.gateway.mqtt.completion-timeout=3s",
				"comanche.gateway.ingestion.registered-device-ids=KIOSK-1",
				"comanche.gateway.ingestion.max-message-size=32KB",
				"comanche.gateway.ingestion.kafka-ack-timeout=4s")
				.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties({GatewayMqttProperties.class, GatewayIngestionProperties.class})
	static class PropertiesConfiguration {
	}
}
