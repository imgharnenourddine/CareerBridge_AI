package com.ai.guild.career_transition_platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CareerTransitionPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareerTransitionPlatformApplication.class, args);
	}

}
