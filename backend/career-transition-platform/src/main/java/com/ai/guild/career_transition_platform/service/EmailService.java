package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.entity.Candidature;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.enums.CandidatureStatus;
import com.ai.guild.career_transition_platform.exception.CandidatureNotFoundException;
import com.ai.guild.career_transition_platform.repository.CandidatureRepository;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
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
	private static final String CANDIDATURE_CONTEXT = "CANDIDATURE";
	private static final Pattern RESPONSE_PATTERN = Pattern.compile("(ACCEPTED|REJECTED)_([0-9]+)", Pattern.CASE_INSENSITIVE);

	private final JavaMailSender mailSender;
	private final CandidatureRepository candidatureRepository;
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

	public void sendFormationInscriptionEmail(String userName, String userEmail, String userPhone, String formationTitle,
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
				Identifiants compte formation:
				  Email: %s
				  Password: %s
				""".formatted(userName, userEmail, userPhone, formationTitle, formationPrice, userEmail, password);

		try {
			SimpleMailMessage msg = new SimpleMailMessage();
			msg.setFrom(fromAddress);
			msg.setTo(NOTIFICATION_INBOX);
			msg.setSubject(subject);
			msg.setText(body);
			mailSender.send(msg);
			log.info("Formation inscription email sent: formationTitle={} to={}", formationTitle, NOTIFICATION_INBOX);
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
		String body = """
				Nouvelle candidature
				Candidat: %s
				Email candidat: %s
				Entreprise cible: %s
				Poste: %s
				Compétences détectées: %s
				Formation complétée: %s
				---
				Pour répondre: reply "ACCEPTED_%d" ou "REJECTED_%d"
				(Envoyez un nouvel email dont le sujet contient [CareerBridge-Response] avec ce texte dans le corps.)
				""".formatted(userName, userEmail, companyName, jobTitle, detectedSkills, formation, id, id);

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
				SearchTerm unseen = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
				SearchTerm subject = new SubjectTerm("[CareerBridge-Response]");
				SearchTerm term = new AndTerm(unseen, subject);
				Message[] messages = inbox.search(term);
				if (messages.length == 0) {
					log.warn("IMAP polling: no unread emails matching subject [CareerBridge-Response]");
					return;
				}
				log.info("IMAP polling: processing {} candidate response email(s)", messages.length);
				for (Message m : messages) {
					try {
						String text = extractText(m);
						log.debug("IMAP email body: {}", text);
						Matcher matcher = RESPONSE_PATTERN.matcher(text);
						if (!matcher.find()) {
							log.warn("IMAP parsing failed: no ACCEPTED_/REJECTED_ pattern in message id={}", m.getMessageNumber());
							continue;
						}
						String action = matcher.group(1).toUpperCase();
						Long candidatureId = Long.parseLong(matcher.group(2));
						String status = "ACCEPTED".equals(action) ? "ACCEPTED" : "REJECTED";
						log.info("IMAP candidature response parsed: candidatureId={} status={}", candidatureId, status);
						self.processCandidatureResponse(candidatureId, status);
						m.setFlag(Flags.Flag.SEEN, true);
					} catch (Exception ex) {
						log.warn("IMAP parsing/processing failed for a message: {}", ex.getMessage());
					}
				}
			}
		} catch (MessagingException e) {
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

		if (newStatus == CandidatureStatus.ACCEPTED) {
			ObjectNode inner = objectMapper.createObjectNode();
			inner.put("type", "TEXT");
			inner.put("text", "Your application to " + candidature.getCompanyName() + " was accepted.");
			ArrayNode options = objectMapper.createArrayNode();
			options.add("View recommendations");
			options.add("Continue");
			inner.set("options", options);
			String content = inner.toString();
			messageService.saveMessage(userId, CANDIDATURE_CONTEXT, "BOT", content, "TEXT");
			log.info("Candidature ACCEPTED: user notified via message candidatureId={}", candidatureId);
		} else {
			ObjectNode inner = objectMapper.createObjectNode();
			inner.put("type", "TEXT");
			inner.put("text", "Your application was not retained. Please choose another company or refine your profile.");
			inner.set("options", objectMapper.createArrayNode().add("Browse companies").add("Back"));
			String content = inner.toString();
			messageService.saveMessage(userId, CANDIDATURE_CONTEXT, "BOT", content, "TEXT");
			log.info("Candidature REJECTED: user notified via message candidatureId={}", candidatureId);
		}
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
