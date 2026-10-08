package io.github.magcal90.comanche_iot_gateway.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

	@Bean
	Clock gatewayClock() {
		return Clock.systemUTC();
	}
}
