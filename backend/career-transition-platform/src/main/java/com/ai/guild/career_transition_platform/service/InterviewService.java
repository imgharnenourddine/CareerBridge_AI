package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.dto.InterviewRespondResponse;
import com.ai.guild.career_transition_platform.dto.InterviewStartResponse;
import com.ai.guild.career_transition_platform.entity.Interview;
import com.ai.guild.career_transition_platform.entity.Recommendation;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.exception.InterviewNotFoundException;
import com.ai.guild.career_transition_platform.repository.FormationRepository;
import com.ai.guild.career_transition_platform.repository.InterviewRepository;
import com.ai.guild.career_transition_platform.repository.RecommendationRepository;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

	public static final String SENDER_USER = "USER";
	public static final String SENDER_BOT = "BOT";
	public static final String TYPE_TEXT = "TEXT";

	private static final String INTERVIEW_SYSTEM_PROMPT = """
			You are an empathetic AI career advisor working for The Forge Guild — a modern career transition ecosystem helping factory workers displaced by automation find new career paths.

			Your role in this interview:
			- Start with a SHORT and warm introduction (max 3 sentences): introduce yourself, mention that history shows workers who lost jobs to automation successfully transitioned to new sectors (like Pittsburgh steel workers who moved into tech and healthcare), and tell the user you will ask a few questions to understand their skills and find the best career path for them.
			- Ask ONE question at a time. Wait for the answer before asking the next.
			- Base each new question on the previous answer to go deeper.
			- You have a MAXIMUM of 7 questions total. Count them carefully.
			- Questions should explore: previous job, years of experience, physical vs technical skills, interests, availability for training, preferred work environment.
			- After the 7th answer (or when you have enough information), say a SHORT closing message (max 2 sentences): confirm you have gathered enough information, and tell the user to check their message inbox for the full analysis.
			- NEVER ask more than 7 questions.
			- Keep all responses SHORT and conversational — this is a voice interview, not a written report.
			- Always respond in the same language the user speaks.""";

	private static final String START_USER_PROMPT = "Begin the interview now with your introduction and your first question.";

	private static final String SKILL_ANALYSIS_USER_PROMPT_TEMPLATE = """
			Based on the following interview transcript (JSON array of {role, content} with roles user and assistant), output ONE JSON object ONLY, no markdown fences, with this exact structure:
			{
			  "detectedSkills": [string],
			  "strengths": [string],
			  "weaknesses": [string],
			  "recommendedSectors": [
			    { "sector": string, "jobTitle": string, "compatibilityScore": number, "reason": string }
			  ]
			}
			Use concise strings. recommendedSectors should be ordered by compatibilityScore descending.

			Transcript JSON:
			%s
			""";

	private final ChatModel mistralChatModel;
	private final InterviewRepository interviewRepository;
	private final UserRepository userRepository;
	private final RecommendationRepository recommendationRepository;
	private final FormationRepository formationRepository;
	private final MessageService messageService;
	private final SpeechService speechService;
	private final ObjectMapper objectMapper;

	@Transactional
	public InterviewStartResponse startInterview(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

		Interview interview = Interview.builder()
				.user(user)
				.transcript("[]")
				.startedAt(LocalDateTime.now())
				.build();
		interview = interviewRepository.save(interview);
		log.info("Interview started: userId={} interviewId={}", userId, interview.getId());

		List<ChatMessage> messages = new ArrayList<>();
		messages.add(SystemMessage.from(INTERVIEW_SYSTEM_PROMPT));
		messages.add(UserMessage.from(START_USER_PROMPT));

		ChatResponse response = mistralChatModel.chat(messages);
		String intro = response.aiMessage().text();
		if (intro == null) {
			intro = "";
		}
		TokenUsage tu = response.tokenUsage();
		if (tu != null) {
			log.debug("Mistral token usage (start): {}", tu);
		}
		log.debug("Introduction text length={}", intro != null ? intro.length() : 0);

		ArrayNode arr = objectMapper.createArrayNode();
		arr.add(objectMapper.createObjectNode().put("role", "assistant").put("content", intro));
		String transcriptJson;
		try {
			transcriptJson = objectMapper.writeValueAsString(arr);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize transcript", e);
		}
		interview.setTranscript(transcriptJson);
		interviewRepository.save(interview);
		log.debug("Transcript serialized after start, length={}", transcriptJson.length());

		String ctx = interviewContext(interview.getId());
		messageService.saveMessage(userId, ctx, SENDER_BOT, intro, TYPE_TEXT);

		SpeechService.SpeechAudio tts = speechService.synthesize(intro);
		byte[] audio = tts != null ? tts.audioBytes() : null;
		String audioCt = tts != null ? tts.contentType() : null;
		if (tts == null) {
			log.warn("TTS failed at interview start; client should use Web Speech API");
		}

		log.info("Interview start completed: interviewId={} introductionLength={}", interview.getId(), intro.length());
		return InterviewStartResponse.builder()
				.interviewId(interview.getId())
				.introductionText(intro)
				.audioBytes(audio)
				.audioContentType(audioCt)
				.build();
	}

	@Transactional
	public InterviewRespondResponse respondToInterview(Long userId, Long interviewId, String userTranscribedText) {
		log.info("User answer received for interview: interviewId={} answerLength={}", interviewId,
				userTranscribedText != null ? userTranscribedText.length() : 0);
		Interview interview = interviewRepository.findByIdAndUser_Id(interviewId, userId)
				.orElseThrow(() -> new InterviewNotFoundException("Interview not found: " + interviewId));

		if (interview.getCompletedAt() != null) {
			throw new IllegalStateException("Interview already completed: " + interviewId);
		}

		List<Map<String, String>> turns = readTranscript(interview.getTranscript());
		List<ChatMessage> messages = new ArrayList<>();
		messages.add(SystemMessage.from(INTERVIEW_SYSTEM_PROMPT));
		for (Map<String, String> turn : turns) {
			String role = turn.getOrDefault("role", "").toLowerCase(Locale.ROOT);
			String content = turn.getOrDefault("content", "");
			if ("user".equals(role)) {
				messages.add(UserMessage.from(content));
			} else if ("assistant".equals(role)) {
				messages.add(AiMessage.from(content));
			}
		}
		String safeUserText = userTranscribedText != null ? userTranscribedText : "";
		messages.add(UserMessage.from(safeUserText));

		log.debug("Prior transcript turns: assistantMessages={} userMessages={}", countRole(turns, "assistant"), countRole(turns, "user"));

		ChatResponse chatResponse = mistralChatModel.chat(messages);
		String aiText = chatResponse.aiMessage().text();
		if (aiText == null) {
			aiText = "";
		}
		TokenUsage tu = chatResponse.tokenUsage();
		if (tu != null) {
			log.debug("Mistral token usage (respond): {}", tu);
		}
		log.info("Interview AI response received: interviewId={} aiLength={}", interviewId, aiText != null ? aiText.length() : 0);

		turns.add(Map.of("role", "user", "content", safeUserText));
		turns.add(Map.of("role", "assistant", "content", aiText));

		String updatedTranscript;
		try {
			updatedTranscript = objectMapper.writeValueAsString(turns);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize transcript", e);
		}
		interview.setTranscript(updatedTranscript);
		log.debug("Transcript updated, length={}", updatedTranscript.length());

		int userCount = countRole(turns, "user");
		int assistantCount = countRole(turns, "assistant");
		log.info("Interview question tracking: assistantMessagesInTranscript={} userMessagesInTranscript={}", assistantCount, userCount);
		log.debug("Transcript counts: userMessages={} assistantMessages={}", userCount, assistantCount);

		boolean closing = isClosingSignal(aiText);
		boolean complete = userCount >= 7 || closing;
		if (complete) {
			interview.setCompletedAt(LocalDateTime.now());
			log.info("Interview marked complete: interviewId={} userMessages={} closingSignal={}", interviewId, userCount, closing);
		}
		interviewRepository.save(interview);

		String ctx = interviewContext(interviewId);
		messageService.saveMessage(userId, ctx, SENDER_USER, safeUserText, TYPE_TEXT);
		messageService.saveMessage(userId, ctx, SENDER_BOT, aiText, TYPE_TEXT);

		SpeechService.SpeechAudio tts = speechService.synthesize(aiText);
		byte[] audio = tts != null ? tts.audioBytes() : null;
		String audioCt = tts != null ? tts.contentType() : null;
		if (tts == null) {
			log.warn("TTS failed after interview response; client should use Web Speech API interviewId={}", interviewId);
		}

		return InterviewRespondResponse.builder()
				.aiResponseText(aiText)
				.audioBytes(audio)
				.audioContentType(audioCt)
				.completed(complete)
				.browserSttRequired(false)
				.build();
	}

	@Transactional
	public void completeInterview(Long userId, Long interviewId) {
		Interview interview = interviewRepository.findByIdAndUser_Id(interviewId, userId)
				.orElseThrow(() -> new InterviewNotFoundException("Interview not found: " + interviewId));
		if (interview.getCompletedAt() == null) {
			throw new IllegalStateException("Interview is not completed yet: " + interviewId);
		}

		String transcriptJson = interview.getTranscript();
		log.info("Completing interview analysis: userId={} interviewId={}", userId, interviewId);
		log.debug("Skill analysis input transcript length={}", transcriptJson != null ? transcriptJson.length() : 0);

		String userPrompt = String.format(SKILL_ANALYSIS_USER_PROMPT_TEMPLATE, transcriptJson != null ? transcriptJson : "[]");
		List<ChatMessage> messages = List.of(
				SystemMessage.from("You output only valid JSON matching the requested schema."),
				UserMessage.from(userPrompt)
		);
		ChatResponse response = mistralChatModel.chat(messages);
		String raw = response.aiMessage().text();
		TokenUsage tu = response.tokenUsage();
		if (tu != null) {
			log.debug("Mistral token usage (analysis): {}", tu);
		}
		String json = stripMarkdownCodeFence(raw);
		log.debug("Skill analysis raw response length={}", raw != null ? raw.length() : 0);

		JsonNode root;
		try {
			root = objectMapper.readTree(json);
		} catch (JsonProcessingException e) {
			log.error("Failed to parse skill analysis JSON: {}", e.getMessage());
			throw new IllegalStateException("Invalid skill analysis JSON from model", e);
		}

		interview.setSkillAnalysis(json);
		interviewRepository.save(interview);

		deleteRecommendationsForInterview(interview);

		User user = interview.getUser();
		JsonNode sectors = root.path("recommendedSectors");
		if (sectors.isArray()) {
			int rank = 1;
			for (JsonNode s : sectors) {
				String sector = s.path("sector").asText("");
				String jobTitle = s.path("jobTitle").asText("");
				String description = buildRecommendationDescription(s, root);
				Recommendation rec = Recommendation.builder()
						.user(user)
						.interview(interview)
						.sector(sector)
						.jobTitle(jobTitle)
						.description(description)
						.rankOrder(rank++)
						.build();
				recommendationRepository.save(rec);
			}
		}

		String analysisSummary = buildAnalysisSummaryMessage(root);
		String analysisCtx = analysisContext(interviewId);
		messageService.saveMessage(userId, analysisCtx, SENDER_BOT, analysisSummary, TYPE_TEXT);
		log.info("Interview analysis saved: interviewId={} recommendations={}", interviewId, sectors.isArray() ? sectors.size() : 0);
	}

	private static String buildRecommendationDescription(JsonNode sectorNode, JsonNode root) {
		String reason = sectorNode.path("reason").asText("");
		double score = sectorNode.path("compatibilityScore").asDouble(Double.NaN);
		StringBuilder sb = new StringBuilder();
		if (!Double.isNaN(score)) {
			sb.append("Compatibility score: ").append(score).append(". ");
		}
		sb.append(reason);
		JsonNode skills = root.path("detectedSkills");
		if (skills.isArray() && !skills.isEmpty()) {
			sb.append(" Detected skills context: ");
			sb.append(skills.toString());
		}
		return sb.toString();
	}

	private static String buildAnalysisSummaryMessage(JsonNode root) {
		StringBuilder sb = new StringBuilder();
		sb.append("Your skill analysis is ready. ");
		JsonNode strengths = root.path("strengths");
		if (strengths.isArray() && strengths.size() > 0) {
			sb.append("Strengths: ");
			sb.append(strengths.toString());
			sb.append(". ");
		}
		sb.append("See your dashboard for sector recommendations.");
		return sb.toString();
	}

	private void deleteRecommendationsForInterview(Interview interview) {
		List<Recommendation> existing = recommendationRepository.findByInterview_Id(interview.getId());
		for (Recommendation r : existing) {
			formationRepository.deleteAll(formationRepository.findByRecommendation_Id(r.getId()));
		}
		recommendationRepository.deleteAll(existing);
	}

	private static String interviewContext(Long interviewId) {
		return "INTERVIEW_" + interviewId;
	}

	private static String analysisContext(Long interviewId) {
		return "ANALYSIS_" + interviewId;
	}

	private List<Map<String, String>> readTranscript(String json) {
		if (json == null || json.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
			});
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid transcript JSON", e);
		}
	}

	private static int countRole(List<Map<String, String>> turns, String role) {
		int n = 0;
		for (Map<String, String> t : turns) {
			if (role.equalsIgnoreCase(t.getOrDefault("role", ""))) {
				n++;
			}
		}
		return n;
	}

	private static boolean isClosingSignal(String text) {
		if (text == null) {
			return false;
		}
		String t = text.toLowerCase(Locale.ROOT);
		return t.contains("message inbox") || t.contains("check your inbox") || t.contains("check their message inbox");
	}

	private static String stripMarkdownCodeFence(String raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.trim();
		if (s.startsWith("```")) {
			int firstNl = s.indexOf('\n');
			if (firstNl > 0) {
				s = s.substring(firstNl + 1);
			}
			int fence = s.lastIndexOf("```");
			if (fence >= 0) {
				s = s.substring(0, fence);
			}
		}
		return s.trim();
	}

	@Transactional(readOnly = true)
	public Interview getCurrentIncompleteInterview(Long userId) {
		return interviewRepository.findFirstByUser_IdAndCompletedAtIsNullOrderByStartedAtDesc(userId).orElse(null);
	}
}
