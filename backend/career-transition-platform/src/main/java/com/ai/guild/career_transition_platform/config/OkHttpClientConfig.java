package com.ai.guild.career_transition_platform.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpClientConfig {

	@Bean
	public OkHttpClient okHttpClient() {
		return new OkHttpClient.Builder()
				.connectTimeout(120, TimeUnit.SECONDS)
				.readTimeout(120, TimeUnit.SECONDS)
				.writeTimeout(120, TimeUnit.SECONDS)
				.callTimeout(180, TimeUnit.SECONDS)
				.build();
	}
}
