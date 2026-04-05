package com.ai.guild.career_transition_platform.controller;

import com.ai.guild.career_transition_platform.dto.CandidatureResponseRequest;
import com.ai.guild.career_transition_platform.dto.MessageDto;
import com.ai.guild.career_transition_platform.dto.PostInterviewResponse;
import com.ai.guild.career_transition_platform.dto.PostInterviewStartRequest;
import com.ai.guild.career_transition_platform.dto.UserChoiceRequest;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.enums.Role;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.ai.guild.career_transition_platform.service.EmailService;
import com.ai.guild.career_transition_platform.service.MessageService;
import com.ai.guild.career_transition_platform.service.PostInterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@RequiredArgsConstructor
@Slf4j
public class PostInterviewController {

	private final PostInterviewService postInterviewService;
	private final MessageService messageService;
	private final EmailService emailService;
	private final UserRepository userRepository;

	@PostMapping("/post-interview/start")
	public ResponseEntity<PostInterviewResponse> start(@RequestBody PostInterviewStartRequest request) {
		User user = currentUser();
		Long interviewId = request.getInterviewId();
		log.info("POST /api/post-interview/start: body accepted interviewId={} userEmail={}", interviewId, user.getEmail());
		PostInterviewResponse body = postInterviewService.startPostInterviewFlow(user.getId(), interviewId);
		log.info("POST /api/post-interview/start: success interviewId={} responseType={} messagePreview={}", interviewId,
				body.getType(), body.getMessage() != null ? body.getMessage().substring(0, Math.min(80, body.getMessage().length())) : "");
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	@PostMapping("/post-interview/choice")
	public ResponseEntity<PostInterviewResponse> choice(@RequestBody UserChoiceRequest request) {
		User user = currentUser();
		Long interviewId = request.getInterviewId();
		log.info("POST /api/post-interview/choice email={} interviewId={} context={}", user.getEmail(), interviewId,
				request.getContext());
		PostInterviewResponse body = postInterviewService.processUserChoice(user.getId(), interviewId, request.getChoice(),
				request.getContext());
		return ResponseEntity.ok(body);
	}

	@GetMapping("/post-interview/{interviewId}/messages")
	public ResponseEntity<List<MessageDto>> messages(@PathVariable Long interviewId) {
		User user = currentUser();
		log.info("GET /api/post-interview/{}/messages email={} interviewId={}", interviewId, user.getEmail(), interviewId);
		String ctx = PostInterviewService.postInterviewContext(interviewId);
		return ResponseEntity.ok(messageService.getMessagesAsDto(user.getId(), ctx));
	}

	@PostMapping("/admin/candidature/respond")
	public ResponseEntity<Void> adminRespond(@RequestBody CandidatureResponseRequest request) {
		User user = currentUser();
		if (user.getRole() != Role.ADMIN) {
			log.warn("Forbidden admin candidature respond attempt email={}", user.getEmail());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		log.info("POST /api/admin/candidature/respond email={} candidatureId={} status={}", user.getEmail(),
				request.getCandidatureId(), request.getStatus());
		emailService.processCandidatureResponse(request.getCandidatureId(), request.getStatus());
		return ResponseEntity.ok().build();
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
