package com.ai.guild.career_transition_platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserChoiceRequest {

	private Long interviewId;
	private String choice;
	private String context;
}
