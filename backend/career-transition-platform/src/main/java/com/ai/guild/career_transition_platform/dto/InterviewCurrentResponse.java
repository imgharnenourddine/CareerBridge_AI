package com.ai.guild.career_transition_platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewCurrentResponse {

	private Long interviewId;
	private LocalDateTime startedAt;
}
