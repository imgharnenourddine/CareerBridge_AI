package com.ai.guild.career_transition_platform.exception;

public class EmailAlreadyExistsException extends RuntimeException {

	public EmailAlreadyExistsException(String email) {
		super("Email already registered: " + email);
	}
}
