package com.ai.guild.career_transition_platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewStartResponse {

	private Long interviewId;
	private String introductionText;
	private byte[] audioBytes;
	private String audioContentType;
}
