package io.github.magcal90.comanche_iot_gateway.config;

import java.util.Arrays;

import io.github.magcal90.comanche_iot_gateway.mqtt.MqttInboundMessageHandler;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "comanche.gateway.mqtt", name = "enabled", havingValue = "true")
public class MqttInboundConfiguration {

	private static final String[] TOPICS = {
			"comanche/devices/+/telemetry",
			"comanche/devices/+/status",
			"comanche/devices/+/events"
	};

	@Bean
	MqttPahoClientFactory mqttClientFactory(
			GatewayMqttProperties properties,
			MqttCredentialProvider credentialProvider) {
		if (properties.username() == null || properties.username().isBlank()) {
			throw new IllegalStateException("comanche.gateway.mqtt.username must be supplied when MQTT is enabled");
		}
		MqttConnectOptions options = new MqttConnectOptions();
		options.setServerURIs(new String[] {properties.serverUri().toString()});
		options.setUserName(properties.username());
		options.setPassword(credentialProvider.requiredPassword());
		options.setCleanSession(false);
		options.setAutomaticReconnect(true);
		options.setConnectionTimeout(Math.toIntExact(properties.connectionTimeout().toSeconds()));
		options.setKeepAliveInterval(Math.toIntExact(properties.keepAliveInterval().toSeconds()));
		options.setMaxReconnectDelay(Math.toIntExact(properties.maxReconnectDelay().toMillis()));

		DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
		factory.setConnectionOptions(options);
		return factory;
	}

	@Bean
	MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(
			GatewayMqttProperties properties,
			MqttPahoClientFactory clientFactory) {
		MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
				properties.clientId(), clientFactory, TOPICS);
		int[] qos = new int[TOPICS.length];
		Arrays.fill(qos, 1);
		adapter.setQos(qos);
		adapter.setManualAcks(true);
		DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter();
		converter.setPayloadAsBytes(true);
		adapter.setConverter(converter);
		adapter.setCompletionTimeout(properties.completionTimeout().toMillis());
		return adapter;
	}

	@Bean
	IntegrationFlow mqttInboundFlow(
			MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter,
			MqttInboundMessageHandler handler) {
		return IntegrationFlow.from(mqttInboundAdapter)
				.handle(handler, "handle")
				.get();
	}
}
