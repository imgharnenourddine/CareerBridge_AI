package com.ai.guild.career_transition_platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewRespondResponse {

	private String aiResponseText;
	private byte[] audioBytes;
	private String audioContentType;
	@JsonProperty("isCompleted")
	private boolean completed;
	/**
	 * True when server-side STT failed; client should transcribe with Web Speech API and resend text.
	 */
	private boolean browserSttRequired;
}
