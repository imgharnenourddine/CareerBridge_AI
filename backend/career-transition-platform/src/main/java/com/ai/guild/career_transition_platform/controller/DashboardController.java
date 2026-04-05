package com.ai.guild.career_transition_platform.controller;

import com.ai.guild.career_transition_platform.dto.DashboardStatsResponse;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.ai.guild.career_transition_platform.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

	private final DashboardService dashboardService;
	private final UserRepository userRepository;

	@GetMapping("/stats")
	public ResponseEntity<DashboardStatsResponse> getStats() {
		User user = currentUser();
		log.info("GET /api/dashboard/stats email={}", user.getEmail());
		return ResponseEntity.ok(dashboardService.getStats(user.getId()));
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
