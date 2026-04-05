package com.ai.guild.career_transition_platform.controller;

import com.ai.guild.career_transition_platform.dto.AuthResponse;
import com.ai.guild.career_transition_platform.dto.LoginRequest;
import com.ai.guild.career_transition_platform.dto.RegisterRequest;
import com.ai.guild.career_transition_platform.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
		log.info("POST /api/auth/register received email={}", request.getEmail());
		AuthResponse body = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
		log.info("POST /api/auth/login received email={}", request.getEmail());
		return ResponseEntity.ok(authService.login(request));
	}
}
