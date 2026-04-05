package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.entity.Candidature;
import com.ai.guild.career_transition_platform.entity.Interview;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.enums.CandidatureStatus;
import com.ai.guild.career_transition_platform.exception.CandidatureNotFoundException;
import com.ai.guild.career_transition_platform.repository.CandidatureRepository;
import com.ai.guild.career_transition_platform.repository.InterviewRepository;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

	@Lazy
	@Autowired
	private EmailService self;

	private static final String NOTIFICATION_INBOX = "imgharnenourddine3@gmail.com";

	private final JavaMailSender mailSender;
	private final CandidatureRepository candidatureRepository;
	private final InterviewRepository interviewRepository;
	private final UserRepository userRepository;
	private final MessageService messageService;
	private final ObjectMapper objectMapper;

	@Value("${spring.mail.username}")
	private String fromAddress;

	@Value("${mail.imap.host}")
	private String imapHost;

	@Value("${mail.imap.port}")
	private int imapPort;

	@Value("${mail.imap.username}")
	private String imapUsername;

	@Value("${mail.imap.password}")
	private String imapPassword;

	@Value("${mail.imap.polling.enabled:true}")
	private boolean imapPollingEnabled;

	public String sendFormationInscriptionEmail(String userName, String userEmail, String userPhone, String formationTitle,
			String formationPrice) {
		String password = String.format("CBG_%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
		String subject = "[CareerBridge] Inscription Formation — " + formationTitle;
		String body = """
				Nouvelle inscription formation
				Nom: %s
				Email: %s
				Téléphone: %s
				Formation: %s
				Prix: %s

				Your account credentials:
				Email: %s
				Password: %s
				⚠️ Please change your password after first login.
				""".formatted(userName, userEmail, userPhone, formationTitle, formationPrice, userEmail, password);

		try {
			SimpleMailMessage msg = new SimpleMailMessage();
			msg.setFrom(fromAddress);
			msg.setTo(NOTIFICATION_INBOX);
			msg.setSubject(subject);
			msg.setText(body);
			mailSender.send(msg);
			log.info("Formation inscription email sent: formationTitle={} to={}", formationTitle, NOTIFICATION_INBOX);
			return password;
		} catch (Exception e) {
			log.error("Failed to send formation inscription email: {}", e.getMessage(), e);
			throw new IllegalStateException("Failed to send formation inscription email", e);
		}
	}

	@Transactional
	public Long sendCandidatureEmail(Long userId, String userName, String userEmail, String companyName, String jobTitle,
			String detectedSkills, String formation) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

		Candidature candidature = Candidature.builder()
				.user(user)
				.companyName(companyName)
				.jobTitle(jobTitle)
				.status(CandidatureStatus.PENDING)
				.build();
		candidature = candidatureRepository.save(candidature);
		Long id = candidature.getId();

		String subject = "[CareerBridge] Candidature — " + userName + " → " + companyName;
		String body = "Nouvelle candidature reçue\n\n"
				+ "Candidat: " + userName + "\n"
				+ "Email: " + userEmail + "\n"
				+ "Entreprise: " + companyName + "\n"
				+ "Poste: " + jobTitle + "\n\n"
				+ "=== INSTRUCTIONS POUR RÉPONDRE ===\n"
				+ "Pour ACCEPTER, répondez avec le sujet exactement:\n"
				+ "[CareerBridge-Response] ACCEPTED_" + id + "\n\n"
				+ "Pour REFUSER, répondez avec le sujet exactement:\n"
				+ "[CareerBridge-Response] REJECTED_" + id + "\n\n"
				+ "IMPORTANT: Le sujet doit contenir exactement [CareerBridge-Response]";
		log.debug("Candidature context: detectedSkills={} formation={}", detectedSkills, formation);

		try {
			SimpleMailMessage msg = new SimpleMailMessage();
			msg.setFrom(fromAddress);
			msg.setTo(NOTIFICATION_INBOX);
			msg.setSubject(subject);
			msg.setText(body);
			mailSender.send(msg);
			log.info("Candidature email sent: candidatureId={} company={} to={}", id, companyName, NOTIFICATION_INBOX);
		} catch (Exception e) {
			log.error("Failed to send candidature email candidatureId={}: {}", id, e.getMessage(), e);
			throw new IllegalStateException("Failed to send candidature email", e);
		}
		return id;
	}

	@Scheduled(fixedDelay = 30000)
	public void startImapPolling() {
		if (!imapPollingEnabled) {
			return;
		}
		try {
			Properties props = new Properties();
			props.put("mail.store.protocol", "imaps");
			props.put("mail.imaps.host", imapHost);
			props.put("mail.imaps.port", String.valueOf(imapPort));
			props.put("mail.imaps.ssl.enable", "true");
			props.put("mail.imaps.ssl.trust", "*");

			Session session = Session.getInstance(props);
			try (Store store = session.getStore("imaps")) {
				store.connect(imapHost, imapPort, imapUsername, imapPassword);
				try (Folder inbox = store.getFolder("INBOX")) {
					inbox.open(Folder.READ_WRITE);

					Calendar cal = Calendar.getInstance();
					cal.add(Calendar.DAY_OF_MONTH, -1);
					ReceivedDateTerm dateTerm = new ReceivedDateTerm(ComparisonTerm.GE, cal.getTime());
					FlagTerm unreadTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
					SearchTerm searchTerm = new AndTerm(dateTerm, unreadTerm);

					Message[] messages = inbox.search(searchTerm);
					log.info("IMAP polling: scanning {} recent unread messages", messages.length);

					for (Message message : messages) {
						try {
							String subject = message.getSubject() != null ? message.getSubject() : "";
							String body = getTextFromMessage(message);
							String combined = subject + "\n" + body;

							Long acceptedId = extractCandidatureId(combined, "ACCEPTED_");
							if (acceptedId != null) {
								log.info("IMAP: ACCEPTED found for candidatureId={}", acceptedId);
								self.processCandidatureResponse(acceptedId, "ACCEPTED");
								message.setFlag(Flags.Flag.SEEN, true);
								continue;
							}

							Long rejectedId = extractCandidatureId(combined, "REJECTED_");
							if (rejectedId != null) {
								log.info("IMAP: REJECTED found for candidatureId={}", rejectedId);
								self.processCandidatureResponse(rejectedId, "REJECTED");
								message.setFlag(Flags.Flag.SEEN, true);
								continue;
							}

							// No token found — leave unread, do not mark
						} catch (Exception e) {
							log.error("IMAP: error processing message num={}: {}", message.getMessageNumber(), e.getMessage(), e);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("IMAP polling failed: {}", e.getMessage(), e);
		}
	}

	@Transactional
	public void processCandidatureResponse(Long candidatureId, String status) {
		Candidature candidature = candidatureRepository.findById(candidatureId)
				.orElseThrow(() -> new CandidatureNotFoundException("Candidature not found: " + candidatureId));
		CandidatureStatus newStatus = "ACCEPTED".equalsIgnoreCase(status) ? CandidatureStatus.ACCEPTED : CandidatureStatus.REJECTED;
		candidature.setStatus(newStatus);
		candidatureRepository.save(candidature);

		User user = candidature.getUser();
		Long userId = user.getId();

		Optional<Interview> latestInterview = interviewRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId);
		String context = latestInterview.map(i -> "POST_INTERVIEW_" + i.getId()).orElse("POST_INTERVIEW");
		log.info("processCandidatureResponse: candidatureId={} status={} messageContext={}", candidatureId, status, context);

		if (newStatus == CandidatureStatus.ACCEPTED) {
			String msg = "🎉 **Congratulations!**\n\n"
					+ "Your application to **" + candidature.getCompanyName() + "** has been **ACCEPTED**!\n\n"
					+ "**What happens next:**\n"
					+ "• The company will contact you within 48 hours\n"
					+ "• Prepare your documents (CV, certificates, ID)\n"
					+ "• Review your formation certificate from " + candidature.getJobTitle() + "\n\n"
					+ "You have successfully completed your career transition journey with **The Forge Guild**! 🏆\n\n"
					+ "Welcome to your new professional life!\n"
					+ "For any assistance: imgharnenourddine3@gmail.com — 0641177339";
			messageService.saveMessage(userId, context, "BOT", buildJson(msg, null, "INFO"), "TEXT");
			log.info("Candidature ACCEPTED: user notified via message candidatureId={}", candidatureId);
		} else {
			String msg = "Unfortunately, **" + candidature.getCompanyName() + "** was not able to offer you a position at this time.\n\n"
					+ "Don't be discouraged — this is a normal part of the job search process!\n\n"
					+ "**Your options:**\n"
					+ "• Apply to another company from our recommendations\n"
					+ "• Contact the company directly for feedback\n"
					+ "• Continue building your skills\n\n"
					+ "Would you like to choose another company?";
			messageService.saveMessage(userId, context, "BOT",
					buildJson(msg, List.of("Show other companies", "I will search on my own"), "COMPANY_CHOICE"), "TEXT");
			log.info("Candidature REJECTED: user notified via message candidatureId={}", candidatureId);
		}
	}

	private Long extractCandidatureId(String text, String prefix) {
		Pattern p = Pattern.compile(Pattern.quote(prefix) + "(\\d+)", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(text);
		if (m.find()) {
			return Long.parseLong(m.group(1));
		}
		return null;
	}

	private String buildJson(String message, List<String> options, String type) {
		try {
			com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
			root.put("message", message);
			if (options == null) {
				root.putNull("options");
			} else {
				com.fasterxml.jackson.databind.node.ArrayNode arr = root.putArray("options");
				for (String o : options) {
					arr.add(o);
				}
			}
			root.put("type", type);
			root.set("data", objectMapper.createObjectNode());
			return objectMapper.writeValueAsString(root);
		} catch (Exception e) {
			log.error("buildJson failed: {}", e.getMessage());
			try {
				return objectMapper.writeValueAsString(Map.of(
						"message", message,
						"options", options,
						"type", type,
						"data", Map.of()));
			} catch (Exception e2) {
				return "{\"message\":\"\",\"options\":null,\"type\":\"INFO\",\"data\":{}}";
			}
		}
	}

	private String getTextFromMessage(Message message) throws MessagingException, IOException {
		return extractText(message);
	}

	private static String extractText(Message m) throws MessagingException, IOException {
		if (m.isMimeType("text/plain")) {
			return m.getContent().toString();
		}
		if (m.isMimeType("multipart/*")) {
			return extractTextFromMultipart((jakarta.mail.internet.MimeMultipart) m.getContent());
		}
		return m.getContent().toString();
	}

	private static String extractTextFromMultipart(jakarta.mail.internet.MimeMultipart multipart)
			throws MessagingException, IOException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < multipart.getCount(); i++) {
			jakarta.mail.BodyPart bp = multipart.getBodyPart(i);
			if (bp.isMimeType("text/plain")) {
				sb.append(bp.getContent().toString());
			} else if (bp.isMimeType("multipart/*")) {
				sb.append(extractTextFromMultipart((jakarta.mail.internet.MimeMultipart) bp.getContent()));
			}
		}
		return sb.toString();
	}
}
