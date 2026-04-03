package com.ai.guild.career_transition_platform.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModelName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class LangChain4jConfig {

	@Bean
	public ChatModel mistralChatModel(@Value("${mistral.api-key}") String apiKey) {
		log.info("Initializing Mistral AI chat model for LangChain4j: model={}", MistralAiChatModelName.MISTRAL_SMALL_LATEST);
		return MistralAiChatModel.builder()
				.apiKey(apiKey)
				.modelName(MistralAiChatModelName.MISTRAL_SMALL_LATEST)
				.build();
	}
}
