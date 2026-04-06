package com.ai.guild.career_transition_platform.controller;

import com.ai.guild.career_transition_platform.dto.ChangePasswordRequest;
import com.ai.guild.career_transition_platform.dto.ProfileResponse;
import com.ai.guild.career_transition_platform.dto.UpdateProfileRequest;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.ai.guild.career_transition_platform.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

	private final ProfileService profileService;
	private final UserRepository userRepository;

	@GetMapping
	public ResponseEntity<ProfileResponse> getProfile() {
		User user = currentUser();
		log.info("GET /api/profile email={}", user.getEmail());
		return ResponseEntity.ok(profileService.getProfile(user.getId()));
	}

	@PutMapping
	public ResponseEntity<ProfileResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
		User user = currentUser();
		log.info("PUT /api/profile email={}", user.getEmail());
		return ResponseEntity.ok(profileService.updateProfile(user.getId(), request));
	}

	@PutMapping("/password")
	public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request) {
		User user = currentUser();
		log.info("PUT /api/profile/password email={}", user.getEmail());
		profileService.changePassword(user.getId(), request);
		return ResponseEntity.ok().build();
	}

	@PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ProfileResponse> uploadPhoto(@RequestParam("file") MultipartFile file) {
		User user = currentUser();
		log.info("POST /api/profile/photo email={}", user.getEmail());
		return ResponseEntity.ok(profileService.uploadProfilePhoto(user.getId(), file));
	}

	private User currentUser() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getName() == null) {
			throw new UsernameNotFoundException("Not authenticated");
		}
		String email = auth.getName();
		return userRepository.findByEmail(email)
				.orElseThrow(() -> {
					log.warn("Authenticated user not found in database email={}", email);
					return new UsernameNotFoundException("User not found: " + email);
				});
	}
}
