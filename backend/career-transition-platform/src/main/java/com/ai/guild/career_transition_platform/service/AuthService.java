package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.dto.AuthResponse;
import com.ai.guild.career_transition_platform.dto.LoginRequest;
import com.ai.guild.career_transition_platform.dto.RegisterRequest;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.enums.Role;
import com.ai.guild.career_transition_platform.exception.EmailAlreadyExistsException;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.ai.guild.career_transition_platform.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final AuthenticationManager authenticationManager;

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		log.info("Registration attempt for email={}", request.getEmail());
		if (userRepository.existsByEmail(request.getEmail())) {
			log.warn("Registration rejected: email already exists email={}", request.getEmail());
			throw new EmailAlreadyExistsException(request.getEmail());
		}

		User user = User.builder()
				.email(request.getEmail())
				.passwordHash(passwordEncoder.encode(request.getPassword()))
				.firstName(request.getFirstName())
				.lastName(request.getLastName())
				.role(Role.USER)
				.build();

		User saved = userRepository.save(user);
		log.info("User registered successfully id={} email={}", saved.getId(), saved.getEmail());

		UserDetails userDetails = userDetailsService.loadUserByUsername(saved.getEmail());
		String token = jwtService.generateToken(userDetails);
		return toAuthResponse(token, saved);
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		log.info("Login attempt for email={}", request.getEmail());
		try {
			authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
		} catch (BadCredentialsException e) {
			log.warn("Login failed (bad credentials) for email={}", request.getEmail());
			throw e;
		}

		User user = userRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> {
					log.error("User not found after successful authentication email={}", request.getEmail());
					return new IllegalStateException("User not found after login");
				});

		UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
		String token = jwtService.generateToken(userDetails);
		log.info("Login successful for email={}", user.getEmail());
		return toAuthResponse(token, user);
	}

	private static AuthResponse toAuthResponse(String token, User user) {
		return AuthResponse.builder()
				.token(token)
				.email(user.getEmail())
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.role(user.getRole())
				.build();
	}
}
