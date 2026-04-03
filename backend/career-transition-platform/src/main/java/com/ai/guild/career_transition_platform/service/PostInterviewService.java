package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.dto.MessageDto;
import com.ai.guild.career_transition_platform.dto.PostInterviewResponse;
import com.ai.guild.career_transition_platform.entity.Interview;
import com.ai.guild.career_transition_platform.entity.Recommendation;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.exception.InterviewNotFoundException;
import com.ai.guild.career_transition_platform.repository.InterviewRepository;
import com.ai.guild.career_transition_platform.repository.RecommendationRepository;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostInterviewService {

	public static final String SENDER_USER = "USER";
	public static final String SENDER_BOT = "BOT";
	public static final String TYPE_TEXT = "TEXT";

	private static final String POST_INTERVIEW_SYSTEM_PROMPT = """
			You are an AI career advisor at The Forge Guild.
			You are in the POST-INTERVIEW phase.
			You already analyzed the user's skills from the interview transcript.
			Your role now is to guide the user step by step through career transition.
			You must ALWAYS respond in JSON format with this exact structure:
			{
			  "message": "Your message text here",
			  "options": ["Option 1 text", "Option 2 text"],
			  "type": "ANALYSIS | SECTOR_CHOICE | FORMATION_CHOICE | INSCRIPTION_CONFIRM | COMPANY_PROPOSAL | APPLICATION_CHOICE | INFO",
			  "data": {}
			}
			Use null for options when no choices are needed.
			Keep messages SHORT and friendly — conversational tone.
			Always respond in the same language the user speaks.
			Base ALL recommendations strictly on the interview transcript and skill analysis — never use generic sectors.""";

	private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?[0-9][0-9\\s]{7,})");

	private final ChatModel mistralChatModel;
	private final InterviewRepository interviewRepository;
	private final RecommendationRepository recommendationRepository;
	private final UserRepository userRepository;
	private final MessageService messageService;
	private final EmailService emailService;
	private final ObjectMapper objectMapper;

	private final ConcurrentHashMap<String, FlowState> flowState = new ConcurrentHashMap<>();

	@Transactional
	public PostInterviewResponse startPostInterviewFlow(Long userId, Long interviewId) {
		Interview interview = interviewRepository.findByIdAndUser_Id(interviewId, userId)
				.orElseThrow(() -> new InterviewNotFoundException("Interview not found: " + interviewId));
		if (interview.getCompletedAt() == null) {
			throw new IllegalStateException("Interview is not completed yet: " + interviewId);
		}
		String skillAnalysis = interview.getSkillAnalysis();
		if (skillAnalysis == null || skillAnalysis.isBlank()) {
			throw new IllegalStateException("Skill analysis is missing for interview: " + interviewId);
		}
		List<Recommendation> recs = recommendationRepository.findByInterview_Id(interviewId);
		String recsSummary = recs.stream()
				.map(r -> r.getSector() + ": " + r.getJobTitle() + " — " + truncate(r.getDescription() != null ? r.getDescription() : "", 200))
				.collect(Collectors.joining("\n"));

		String userPrompt = """
				Based on this skill analysis JSON:
				%s

				And these recommendations (same interview):
				%s

				Generate a friendly analysis message explaining the user's detected skills and strengths, then ask if they want to explore career paths further.
				Respond ONLY in the JSON structure specified in the system prompt. Set type to ANALYSIS and include 2 short options (e.g. Yes / Not now) in the user's language.
				""".formatted(skillAnalysis, recsSummary.isBlank() ? "(none)" : recsSummary);

		String raw = callMistral(userPrompt);
		log.debug("Mistral post-interview start raw: {}", raw);
		PostInterviewResponse parsed = parseAssistantJson(raw);
		String ctx = postInterviewContext(interviewId);
		String toSave = raw != null ? stripMarkdownCodeFence(raw) : objectMapper.valueToTree(parsed).toString();
		messageService.saveMessage(userId, ctx, SENDER_BOT, toSave, TYPE_TEXT);
		log.info("Post-interview flow started: userId={} interviewId={} type={}", userId, interviewId, parsed.getType());
		return parsed;
	}

	@Transactional
	public PostInterviewResponse processUserChoice(Long userId, Long interviewId, String userChoice, String context) {
		Interview interview = interviewRepository.findByIdAndUser_Id(interviewId, userId)
				.orElseThrow(() -> new InterviewNotFoundException("Interview not found: " + interviewId));
		String skillAnalysis = interview.getSkillAnalysis();
		if (skillAnalysis == null || skillAnalysis.isBlank()) {
			throw new IllegalStateException("Skill analysis is missing for interview: " + interviewId);
		}
		String ctx = postInterviewContext(interviewId);
		messageService.saveMessage(userId, ctx, SENDER_USER, userChoice != null ? userChoice : "", TYPE_TEXT);

		String history = buildHistoryFromMessages(messageService.getMessagesAsDto(userId, ctx));
		User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
		String key = flowKey(userId, interviewId);
		FlowState state = flowState.computeIfAbsent(key, k -> new FlowState());

		String userPrompt = buildPromptForContext(context, userChoice, skillAnalysis, history, user, state);
		log.info("Post-interview choice: userId={} interviewId={} context={}", userId, interviewId, context);

		String raw;
		if ("INSCRIPTION_CONFIRMED".equalsIgnoreCase(context)) {
			String phone = extractPhone(userChoice);
			String userName = user.getFirstName() + " " + user.getLastName();
			String price = state.formationPrice != null ? state.formationPrice : "N/A";
			String title = state.formationTitle != null ? state.formationTitle : "Formation";
			emailService.sendFormationInscriptionEmail(userName, user.getEmail(), phone, title, price);
			log.info("Formation inscription email dispatched for userId={} interviewId={}", userId, interviewId);
			userPrompt = """
					The inscription notification email was just sent to the admin inbox.
					Skill analysis (for grounding): %s
					User sector context: %s
					Now respond in JSON: confirm credentials were emailed, then propose exactly 3 simulated employers in Morocco for this user with: company name, role, salary range in MAD, contact email imgharnenourddine3@gmail.com, phone 0641177339.
					Put structured details in data.companies as an array.
					""".formatted(skillAnalysis, state.sector != null ? state.sector : "");
			raw = callMistral(userPrompt);
		} else if ("APPLICATION_AI".equalsIgnoreCase(context)) {
			if (state.companyName == null || state.companyName.isBlank()) {
				state.companyName = userChoice != null ? userChoice : "Target company";
			}
			if (state.jobTitle == null || state.jobTitle.isBlank()) {
				state.jobTitle = "Applied role";
			}
			String skills = extractDetectedSkills(skillAnalysis);
			String formation = state.formationTitle != null ? state.formationTitle : "N/A";
			String company = state.companyName != null ? state.companyName : "Target company";
			String job = state.jobTitle != null ? state.jobTitle : "Role";
			emailService.sendCandidatureEmail(userId, user.getFirstName() + " " + user.getLastName(), user.getEmail(),
					company, job, skills, formation);
			log.info("Candidature email dispatched from APPLICATION_AI userId={} interviewId={}", userId, interviewId);
			userPrompt = """
					The application was emailed to the admin inbox for processing.
					Tell the user they will be notified in the app inbox. Respond in JSON only. type INFO.
					""";
			raw = callMistral(userPrompt);
		} else {
			raw = callMistral(userPrompt);
		}

		log.debug("Mistral post-interview raw: {}", raw);
		PostInterviewResponse parsed = parseAssistantJson(raw);
		updateStateFromContext(context, userChoice, parsed, state);

		String toSave = raw != null ? stripMarkdownCodeFence(raw) : objectMapper.valueToTree(parsed).toString();
		messageService.saveMessage(userId, ctx, SENDER_BOT, toSave, TYPE_TEXT);
		log.info("Post-interview step completed: userId={} interviewId={} responseType={}", userId, interviewId, parsed.getType());
		return parsed;
	}

	private String buildPromptForContext(String context, String choice, String skillAnalysis, String history, User user,
			FlowState state) {
		if (context == null) {
			context = "";
		}
		String c = context.trim();
		String ch = choice != null ? choice : "";

		if ("ANALYSIS_RESPONSE".equalsIgnoreCase(c)) {
			boolean yes = ch.toLowerCase(Locale.ROOT).contains("yes") || ch.toLowerCase(Locale.ROOT).contains("oui")
					|| ch.toLowerCase(Locale.ROOT).contains("si");
			if (!yes) {
				return """
						The user declined to explore paths now. Acknowledge kindly and invite them to return. JSON only, type INFO, options null.
						History:\s""" + history;
			}
			return """
					Skill analysis JSON:\s
					%s

					Propose exactly 3 sectors tailored to THIS user (not generic). For each sector include: name, why it fits their skills, and 2-3 job titles.
					Put details in data.sectors as an array. type SECTOR_CHOICE. Include 3 short options matching the sectors.
					Conversation:\s
					%s
					""".formatted(skillAnalysis, history);
		}

		if ("SECTOR_CHOICE".equalsIgnoreCase(c)) {
			state.sector = ch;
			return """
					The user chose this sector: %s
					Skill analysis:\s
					%s

					Propose 3 training programs in Morocco for this sector with simulated prices in MAD, duration, and short description.
					Put programs in data.programs. type FORMATION_CHOICE. options should list 3 program titles briefly.
					History:\s
					%s
					""".formatted(ch, skillAnalysis, history);
		}

		if ("FORMATION_CHOICE".equalsIgnoreCase(c)) {
			state.formationTitle = ch;
			state.formationPrice = guessPriceFromChoice(ch);
			return """
					The user chose this formation option text: %s
					Ask if they want you to handle inscription or they prefer self-service. JSON type INSCRIPTION_CONFIRM. options: ["You handle it", "I'll do it myself"] (translate to user's language if needed).
					History:\s
					%s
					""".formatted(ch, history);
		}

		if ("INSCRIPTION_SELF_MANAGE".equalsIgnoreCase(c)) {
			return """
					Tell the user to send name, email, and phone as a message. JSON type INFO.
					History:\s
					""" + history;
		}

		if ("INSCRIPTION_AI_MANAGE".equalsIgnoreCase(c)) {
			return """
					Show the user's existing profile info and ask to confirm:
					Name: %s %s
					Email: %s
					(JSON only, type INSCRIPTION_CONFIRM, include options Yes/No)
					History:\s
					%s
					""".formatted(user.getFirstName(), user.getLastName(), user.getEmail(), history);
		}

		if ("COMPANY_CHOICE".equalsIgnoreCase(c)) {
			return """
					Ask whether the user wants you to submit the application for them or prefers to apply themselves.
					JSON type APPLICATION_CHOICE, options ["You apply for me", "I'll apply myself"] (adapt language).
					Context choice text: %s
					History:\s
					%s
					""".formatted(ch, history);
		}

		if ("APPLICATION_SELF".equalsIgnoreCase(c)) {
			return """
					Provide practical tips and remind contact email imgharnenourddine3@gmail.com and phone 0641177339 as examples. JSON type INFO.
					History:\s
					""" + history;
		}

		return """
				Context=%s user choice=%s
				Skill analysis:
				%s
				Continue the post-interview flow in JSON format as specified. Use conversation history:
				%s
				""".formatted(c, ch, skillAnalysis, history);
	}

	private void updateStateFromContext(String context, String choice, PostInterviewResponse parsed, FlowState state) {
		if (context != null && "SECTOR_CHOICE".equalsIgnoreCase(context.trim())) {
			state.sector = choice;
		}
		if (parsed != null && parsed.getData() != null && parsed.getData().has("companies")) {
			JsonNode companies = parsed.getData().get("companies");
			if (companies.isArray() && !companies.isEmpty()) {
				JsonNode first = companies.get(0);
				state.companyName = first.path("name").asText("");
				state.jobTitle = first.path("role").asText("");
			}
		}
		if (parsed != null && "COMPANY_PROPOSAL".equalsIgnoreCase(parsed.getType())) {
			if (parsed.getData() != null && parsed.getData().has("companies") && parsed.getData().get("companies").isArray()) {
				JsonNode arr = parsed.getData().get("companies");
				if (!arr.isEmpty()) {
					state.companyName = arr.get(0).path("name").asText(state.companyName);
					state.jobTitle = arr.get(0).path("role").asText(state.jobTitle);
				}
			}
		}
	}

	private static String flowKey(Long userId, Long interviewId) {
		return userId + ":" + interviewId;
	}

	public static String postInterviewContext(Long interviewId) {
		return "POST_INTERVIEW_" + interviewId;
	}

	private String callMistral(String userPrompt) {
		try {
			List<ChatMessage> messages = List.of(
					SystemMessage.from(POST_INTERVIEW_SYSTEM_PROMPT),
					UserMessage.from(userPrompt)
			);
			ChatResponse response = mistralChatModel.chat(messages);
			return response.aiMessage().text();
		} catch (Exception e) {
			log.error("Mistral call failed in post-interview flow: {}", e.getMessage(), e);
			throw new IllegalStateException("Mistral call failed", e);
		}
	}

	private PostInterviewResponse parseAssistantJson(String raw) {
		if (raw == null || raw.isBlank()) {
			return PostInterviewResponse.builder()
					.message("Sorry, something went wrong.")
					.type("INFO")
					.build();
		}
		String json = stripMarkdownCodeFence(raw);
		try {
			JsonNode root = objectMapper.readTree(json);
			List<String> options = null;
			if (root.has("options") && root.get("options").isArray()) {
				options = new ArrayList<>();
				for (JsonNode n : root.get("options")) {
					options.add(n.asText());
				}
			}
			return PostInterviewResponse.builder()
					.message(root.path("message").asText(""))
					.options(options)
					.type(root.path("type").asText("INFO"))
					.data(root.path("data"))
					.build();
		} catch (JsonProcessingException e) {
			log.error("Failed to parse post-interview JSON: {}", e.getMessage());
			return PostInterviewResponse.builder()
					.message(raw)
					.type("INFO")
					.build();
		}
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

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max) + "...";
	}

	private static String buildHistoryFromMessages(List<MessageDto> messages) {
		StringBuilder sb = new StringBuilder();
		for (MessageDto m : messages) {
			sb.append(m.getSender()).append(": ").append(m.getContent()).append("\n");
		}
		return sb.toString();
	}

	private String extractDetectedSkills(String skillAnalysis) {
		try {
			JsonNode n = objectMapper.readTree(skillAnalysis);
			JsonNode ds = n.path("detectedSkills");
			if (ds.isArray()) {
				return ds.toString();
			}
		} catch (Exception ignored) {
			// ignore
		}
		return skillAnalysis;
	}

	private static String extractPhone(String choice) {
		if (choice == null) {
			return "N/A";
		}
		Matcher m = PHONE_PATTERN.matcher(choice);
		if (m.find()) {
			return m.group(1).trim();
		}
		return "N/A";
	}

	private static String guessPriceFromChoice(String choice) {
		if (choice == null) {
			return "N/A";
		}
		Matcher m = Pattern.compile("([0-9]{2,6})\\s*MAD", Pattern.CASE_INSENSITIVE).matcher(choice);
		if (m.find()) {
			return m.group(1) + " MAD";
		}
		return "N/A";
	}

	private static final class FlowState {
		private String sector;
		private String formationTitle;
		private String formationPrice;
		private String companyName;
		private String jobTitle;
	}
}
