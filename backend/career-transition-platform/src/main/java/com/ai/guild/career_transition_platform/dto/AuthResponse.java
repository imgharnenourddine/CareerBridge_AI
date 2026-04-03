package com.ai.guild.career_transition_platform.dto;

import com.ai.guild.career_transition_platform.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

	private String token;
	private String email;
	private String firstName;
	private String lastName;
	private Role role;
}
