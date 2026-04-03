package com.ai.guild.career_transition_platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileResponse {

	private Long id;
	private String email;
	private String firstName;
	private String lastName;
	private String imagePath;
}
