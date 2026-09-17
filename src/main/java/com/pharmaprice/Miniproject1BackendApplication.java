package com.pharmaprice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.pharmaprice.common.config.RecommendationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RecommendationProperties.class)
public class Miniproject1BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(Miniproject1BackendApplication.class, args);
	}

}
