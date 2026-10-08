package io.github.magcal90.comanche_operations_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ComancheOperationsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ComancheOperationsServiceApplication.class, args);
	}

}
