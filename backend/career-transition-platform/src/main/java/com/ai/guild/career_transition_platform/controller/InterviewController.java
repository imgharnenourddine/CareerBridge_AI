package com.ai.guild.career_transition_platform.controller;

import com.ai.guild.career_transition_platform.dto.InterviewCurrentResponse;
import com.ai.guild.career_transition_platform.dto.InterviewRespondResponse;
import com.ai.guild.career_transition_platform.dto.InterviewStartResponse;
import com.ai.guild.career_transition_platform.dto.MessageDto;
import com.ai.guild.career_transition_platform.entity.Interview;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.exception.InterviewNotFoundException;
import com.ai.guild.career_transition_platform.repository.InterviewRepository;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.ai.guild.career_transition_platform.service.InterviewService;
import com.ai.guild.career_transition_platform.service.MessageService;
import com.ai.guild.career_transition_platform.service.SpeechService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@RequiredArgsConstructor
@Slf4j
public class InterviewController {

	private final InterviewService interviewService;
	private final MessageService messageService;
	private final SpeechService speechService;
	private final UserRepository userRepository;
	private final InterviewRepository interviewRepository;

	@PostMapping("/start")
	public ResponseEntity<InterviewStartResponse> startInterview() {
		User user = currentUser();
		InterviewStartResponse body = interviewService.startInterview(user.getId());
		log.info("POST /api/interview/start email={} interviewId={}", user.getEmail(), body.getInterviewId());
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	@PostMapping(value = "/{interviewId}/respond", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<InterviewRespondResponse> respond(
			@PathVariable Long interviewId,
			@RequestParam(value = "audio", required = false) MultipartFile audio,
			@RequestParam(value = "text", required = false) String text) {
		User user = currentUser();
		log.info("POST /api/interview/{}/respond email={} interviewId={}", interviewId, user.getEmail(), interviewId);

		String transcribed = null;
		if (text != null && !text.isBlank()) {
			transcribed = text.trim();
			log.info("Using client-provided transcript text for interviewId={}", interviewId);
		} else if (audio != null && !audio.isEmpty()) {
			try {
				byte[] bytes = audio.getBytes();
				String filename = audio.getOriginalFilename() != null ? audio.getOriginalFilename() : "audio.webm";
				String contentType = audio.getContentType() != null ? audio.getContentType() : "application/octet-stream";
				transcribed = speechService.transcribe(bytes, filename, contentType);
			} catch (Exception e) {
				log.error("Unexpected error reading audio for interviewId={}: {}", interviewId, e.getMessage(), e);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
			}
		} else {
			throw new IllegalArgumentException("Either audio or text must be provided");
		}

		if (transcribed == null) {
			log.warn("STT unavailable for interviewId={}; signaling browser Web Speech API", interviewId);
			return ResponseEntity.ok(InterviewRespondResponse.builder()
					.aiResponseText(null)
					.audioBytes(null)
					.audioContentType(null)
					.completed(false)
					.browserSttRequired(true)
					.build());
		}

		InterviewRespondResponse body = interviewService.respondToInterview(user.getId(), interviewId, transcribed);
		return ResponseEntity.ok(body);
	}

	@GetMapping("/{interviewId}/messages")
	public ResponseEntity<List<MessageDto>> messages(@PathVariable Long interviewId) {
		User user = currentUser();
		log.info("GET /api/interview/{}/messages email={} interviewId={}", interviewId, user.getEmail(), interviewId);
		interviewRepository.findByIdAndUser_Id(interviewId, user.getId())
				.orElseThrow(() -> new InterviewNotFoundException("Interview not found: " + interviewId));
		String ctx = interviewContext(interviewId);
		return ResponseEntity.ok(messageService.getMessagesAsDto(user.getId(), ctx));
	}

	@GetMapping("/current")
	public ResponseEntity<InterviewCurrentResponse> currentInterview() {
		User user = currentUser();
		// Latest interview by createdAt DESC (any status) — Messages uses this for the most recent session id
		InterviewCurrentResponse body = interviewService.getCurrentInterview(user.getId());
		log.info("GET /api/interview/current email={} interviewId={}", user.getEmail(), body.getInterviewId());
		return ResponseEntity.ok(body);
	}

	/**
	 * Completed interviews for the user, newest completion first — used to list separate post-interview conversations.
	 */
	@GetMapping("/completed")
	public ResponseEntity<List<Map<String, Object>>> completedInterviews() {
		User user = currentUser();
		List<Interview> all = interviewRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
		List<Interview> completed = all.stream()
				.filter(i -> i.getCompletedAt() != null)
				.sorted(Comparator.comparing(Interview::getCompletedAt).reversed())
				.toList();
		List<Map<String, Object>> out = new ArrayList<>(completed.size());
		for (Interview iv : completed) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("interviewId", iv.getId());
			row.put("completedAt", iv.getCompletedAt());
			out.add(row);
		}
		log.info("GET /api/interview/completed email={} count={}", user.getEmail(), out.size());
		return ResponseEntity.ok(out);
	}

	@PostMapping("/{interviewId}/complete")
	public ResponseEntity<Void> completeInterview(@PathVariable Long interviewId) {
		User user = currentUser();
		log.info("POST /api/interview/{}/complete email={} interviewId={}", interviewId, user.getEmail(), interviewId);
		interviewService.completeInterview(user.getId(), interviewId);
		return ResponseEntity.noContent().build();
	}

	private static String interviewContext(Long interviewId) {
		return "INTERVIEW_" + interviewId;
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
