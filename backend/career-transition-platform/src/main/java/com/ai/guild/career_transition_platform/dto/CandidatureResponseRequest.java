package com.ai.guild.career_transition_platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidatureResponseRequest {

	private Long candidatureId;
	/**
	 * ACCEPTED or REJECTED
	 */
	private String status;
}
