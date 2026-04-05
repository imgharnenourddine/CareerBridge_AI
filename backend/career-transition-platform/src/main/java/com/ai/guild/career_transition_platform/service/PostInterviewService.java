package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.dto.PostInterviewResponse;
import com.ai.guild.career_transition_platform.entity.Formation;
import com.ai.guild.career_transition_platform.entity.Interview;
import com.ai.guild.career_transition_platform.entity.Message;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.exception.InterviewNotFoundException;
import com.ai.guild.career_transition_platform.repository.FormationRepository;
import com.ai.guild.career_transition_platform.repository.InterviewRepository;
import com.ai.guild.career_transition_platform.repository.MessageRepository;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostInterviewService {

	public static final String SENDER_USER = "USER";
	public static final String SENDER_BOT = "BOT";
	public static final String TYPE_TEXT = "TEXT";

	private static final String SECTOR_1 = "Industrial Maintenance & Automation";
	private static final String SECTOR_2 = "Logistics & Supply Chain Management";
	private static final String SECTOR_3 = "Healthcare Technology Support";

	private static final String OPT_SECTOR_1 = "Industrial Maintenance & Automation";
	private static final String OPT_SECTOR_2 = "Logistics & Supply Chain";
	private static final String OPT_SECTOR_3 = "Healthcare Technology Support";

	private static final String FIXED_SKILLS =
			"Manual dexterity, Machine operation, Technical problem-solving, Process optimization, Team coordination";

	private static final List<SimFormation> FORMATIONS_S1 = List.of(
			new SimFormation(SECTOR_1, "CNC Machine Technician Certification", "3 months", "4,500 MAD",
					"Learn to operate, program and maintain CNC machines used in modern factories. Includes hands-on training with Fanuc and Siemens controllers.",
					"OFPPT Casablanca — Institut Spécialisé de Technologie Appliquée",
					"CNC Machine Technician - 3 months - 4,500 MAD"),
			new SimFormation(SECTOR_1, "Industrial Automation & PLC Programming", "6 months", "8,200 MAD",
					"Master Programmable Logic Controllers (PLC), SCADA systems, and industrial robotics. Siemens S7-300/400 certification included.",
					"Centre de Formation Professionnelle ISTA Ain Sebaa",
					"Industrial Automation - 6 months - 8,200 MAD"),
			new SimFormation(SECTOR_1, "Preventive Maintenance Technician", "4 months", "5,800 MAD",
					"Comprehensive training in mechanical, electrical and hydraulic maintenance for industrial equipment. Job placement assistance included.",
					"OFPPT Mohammedia — Secteur Industrie",
					"Preventive Maintenance - 4 months - 5,800 MAD"));

	private static final List<SimFormation> FORMATIONS_S2 = List.of(
			new SimFormation(SECTOR_2, "Warehouse Management & WMS Systems", "2 months", "3,200 MAD",
					"Master modern warehouse operations, inventory management, and Warehouse Management Systems (SAP WM, Manhattan). CILT certification prep.",
					"Logistics Training Center — Casablanca Port Zone",
					"Warehouse Management & WMS Systems - 2 months - 3,200 MAD"),
			new SimFormation(SECTOR_2, "Supply Chain Operations Coordinator", "4 months", "6,500 MAD",
					"End-to-end supply chain management including procurement, transport coordination, and ERP systems (SAP, Oracle). Internship guaranteed.",
					"ISCAE Casablanca — Formation Continue",
					"Supply Chain Operations Coordinator - 4 months - 6,500 MAD"),
			new SimFormation(SECTOR_2, "Transport & Distribution Manager", "3 months", "4,800 MAD",
					"Fleet management, route optimization, customs procedures and international logistics. Includes TMS software training.",
					"Institut Supérieur de Transport et de Logistique — Rabat",
					"Transport & Distribution Manager - 3 months - 4,800 MAD"));

	private static final List<SimFormation> FORMATIONS_S3 = List.of(
			new SimFormation(SECTOR_3, "Biomedical Equipment Technician", "6 months", "9,500 MAD",
					"Maintenance and repair of medical devices including imaging systems, patient monitors, and laboratory equipment. Hospital internship included.",
					"Institut Supérieur des Professions Infirmières — Casablanca",
					"Biomedical Equipment Technician - 6 months - 9,500 MAD"),
			new SimFormation(SECTOR_3, "Medical IT Support Specialist", "3 months", "5,200 MAD",
					"IT infrastructure in healthcare settings, Electronic Health Records (EHR) systems, PACS/RIS systems, and DICOM standards.",
					"OFPPT — Secteur Santé Casablanca",
					"Medical IT Support Specialist - 3 months - 5,200 MAD"),
			new SimFormation(SECTOR_3, "Sterilization & Medical Device Processing", "2 months", "2,800 MAD",
					"ISO 13485 standards for medical device sterilization, packaging, and quality control. Immediate job placement in clinics and hospitals.",
					"Centre Hospitalier Ibn Rochd — Formation Continue",
					"Sterilization & Medical Device Processing - 2 months - 2,800 MAD"));

	private static final List<SimCompany> COMPANIES_S1 = List.of(
			new SimCompany("Renault Maroc — Usine de Casablanca", "Industrial Maintenance Technician",
					"5,500 — 7,000 MAD/month", "imgharnenourddine3@gmail.com", "0641177339",
					"Route de Rabat, Ain Sebaa, Casablanca", "Renault Maroc — 5,500-7,000 MAD"),
			new SimCompany("Leoni Wire — Tanger", "Automation Maintenance Engineer", "6,000 — 8,500 MAD/month",
					"imgharnenourddine3@gmail.com", "0641177339", "Zone Franche de Tanger, Tanger",
					"Leoni Wire — 6,000-8,500 MAD"),
			new SimCompany("OCP Group — Jorf Lasfar", "Mechanical Maintenance Specialist", "7,000 — 10,000 MAD/month",
					"imgharnenourddine3@gmail.com", "0641177339", "Jorf Lasfar Industrial Zone, El Jadida",
					"OCP Group — 7,000-10,000 MAD"));

	private static final List<SimCompany> COMPANIES_S2 = List.of(
			new SimCompany("Marsa Maroc — Port de Casablanca", "Logistics Operations Coordinator",
					"5,000 — 6,500 MAD/month", "imgharnenourddine3@gmail.com", "0641177339",
					"Port de Casablanca, Casablanca", "Marsa Maroc — 5,000-6,500 MAD"),
			new SimCompany("DHL Maroc", "Warehouse Supervisor", "5,500 — 7,000 MAD/month",
					"imgharnenourddine3@gmail.com", "0641177339", "Zone Industrielle Ouled Salah, Casablanca",
					"DHL Maroc — 5,500-7,000 MAD"),
			new SimCompany("Décathlon Maroc — Distribution Center", "Supply Chain Operator", "4,500 — 6,000 MAD/month",
					"imgharnenourddine3@gmail.com", "0641177339", "Bouskoura Industrial Park, Casablanca",
					"Décathlon Maroc — 4,500-6,000 MAD"));

	private static final List<SimCompany> COMPANIES_S3 = List.of(
			new SimCompany("Clinique Internationale de Casablanca", "Biomedical Equipment Technician",
					"5,500 — 7,500 MAD/month", "imgharnenourddine3@gmail.com", "0641177339",
					"Boulevard Ghandi, Casablanca", "Clinique Internationale — 5,500-7,500 MAD"),
			new SimCompany("CHU Ibn Rochd — Casablanca", "Medical Device Maintenance Specialist",
					"5,000 — 6,500 MAD/month", "imgharnenourddine3@gmail.com", "0641177339",
					"Rue des Hôpitaux, Casablanca", "CHU Ibn Rochd — 5,000-6,500 MAD"),
			new SimCompany("Sanofi Maroc", "Laboratory Equipment Technician", "6,500 — 9,000 MAD/month",
					"imgharnenourddine3@gmail.com", "0641177339", "Zone Industrielle Sidi Bernoussi, Casablanca",
					"Sanofi Maroc — 6,500-9,000 MAD"));

	private final InterviewRepository interviewRepository;
	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final FormationRepository formationRepository;
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
		String context = "POST_INTERVIEW_" + interviewId;
		List<Message> existing = messageRepository.findByUser_IdAndContextOrderByOrderIndexAsc(userId, context);
		if (!existing.isEmpty()) {
			for (Message msg : existing) {
				PostInterviewResponse parsed = buildResponseFromMessage(msg);
				if (parsed != null) {
					log.info("Post-interview already started for interviewId={} — returning existing message payload (no duplicate save)",
							interviewId);
					return parsed;
				}
			}
			log.warn("Post-interview rows exist for interviewId={} but none parsed; saving welcome again may duplicate — check data",
					interviewId);
		}
		String welcomeJson = buildWelcomeJson();
		messageService.saveMessage(userId, context, SENDER_BOT, welcomeJson, TYPE_TEXT);
		log.info("Post-interview started for interviewId={} — welcome message saved", interviewId);
		return parseJsonToResponse(welcomeJson);
	}

	private String buildWelcomeJson() {
		String message = "✅ Your interview analysis is complete!\n\n"
				+ "**Detected Skills**\n"
				+ "• Manual dexterity & precision work\n"
				+ "• Industrial machine operation\n"
				+ "• Technical problem-solving\n"
				+ "• Process optimization\n"
				+ "• Team coordination & communication\n\n"
				+ "**Strengths**\n"
				+ "• Strong hands-on experience in industrial environments\n"
				+ "• High adaptability to technical challenges\n"
				+ "• Physical endurance and attention to detail\n\n"
				+ "Based on your profile, we identified **3 compatible career sectors** for you:\n\n"
				+ "🔧 **Industrial Maintenance & Automation**\n"
				+ "Your machine operation experience maps directly to maintenance technician roles in modern factories. Compatibility: 92%\n\n"
				+ "📦 **Logistics & Supply Chain Management**\n"
				+ "Your factory coordination and process skills are highly valued in modern logistics and warehousing. Compatibility: 85%\n\n"
				+ "🏥 **Healthcare Technology Support**\n"
				+ "Your precision and problem-solving abilities are needed in medical equipment maintenance and support. Compatibility: 78%\n\n"
				+ "Which sector interests you most?";
		PostInterviewResponse response = PostInterviewResponse.builder()
				.message(message)
				.options(List.of(OPT_SECTOR_1, OPT_SECTOR_2, OPT_SECTOR_3))
				.type("SECTOR_CHOICE")
				.data(objectMapper.createObjectNode())
				.build();
		return toJsonContent(response);
	}

	private PostInterviewResponse parseJsonToResponse(String json) {
		return parsePostInterviewPayload(json);
	}

	private PostInterviewResponse buildResponseFromMessage(Message m) {
		if (m == null || m.getBody() == null || m.getBody().isBlank()) {
			return null;
		}
		try {
			JsonNode wrapper = objectMapper.readTree(m.getBody());
			String content = wrapper.path("content").asText("");
			return parsePostInterviewPayload(content);
		} catch (JsonProcessingException e) {
			log.debug("buildResponseFromMessage: invalid message body: {}", e.getMessage());
			return null;
		}
	}

	private PostInterviewResponse parsePostInterviewPayload(String content) {
		if (content == null || content.isBlank()) {
			return null;
		}
		String json = content.trim();
		if (!json.startsWith("{")) {
			return null;
		}
		try {
			JsonNode root = objectMapper.readTree(json);
			List<String> options = null;
			if (root.has("options") && !root.get("options").isNull() && root.get("options").isArray()) {
				options = new ArrayList<>();
				for (JsonNode n : root.get("options")) {
					options.add(n.asText());
				}
			}
			JsonNode dataNode = root.path("data");
			return PostInterviewResponse.builder()
					.message(root.path("message").asText(""))
					.options(options)
					.type(root.path("type").asText("INFO"))
					.data(dataNode.isMissingNode() || dataNode.isNull() ? objectMapper.createObjectNode() : dataNode)
					.build();
		} catch (JsonProcessingException e) {
			log.debug("Could not parse post-interview bot payload: {}", e.getMessage());
			return null;
		}
	}

	@Transactional
	public PostInterviewResponse processUserChoice(Long userId, Long interviewId, String userChoice, String context) {
		interviewRepository.findByIdAndUser_Id(interviewId, userId)
				.orElseThrow(() -> new InterviewNotFoundException("Interview not found: " + interviewId));
		String ctx = postInterviewContext(interviewId);
		messageService.saveMessage(userId, ctx, SENDER_USER, userChoice != null ? userChoice : "", TYPE_TEXT);

		User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
		String key = flowKey(userId, interviewId);
		FlowState state = flowState.computeIfAbsent(key, k -> new FlowState());

		String c = context == null ? "" : context.trim();
		String cUpper = c.toUpperCase(Locale.ROOT);
		String choice = userChoice != null ? userChoice : "";

		PostInterviewResponse response = switch (cUpper) {
			case "SECTOR_CHOICE" -> handleSectorChoice(state, choice);
			case "FORMATION_CHOICE" -> handleFormationChoice(user, state, choice);
			case "INSCRIPTION_CHOICE" -> handleInscriptionChoice(user, state, choice);
			case "INSCRIPTION_CONFIRM" -> handleInscriptionConfirm(user, state, choice);
			case "COMPANY_PROPOSAL_TRIGGER" -> handleCompanyProposalTrigger(state);
			case "COMPANY_CHOICE" -> handleCompanyChoice(state, choice);
			case "APPLICATION_CHOICE" -> handleApplicationChoice(userId, user, state, choice);
			default -> PostInterviewResponse.builder()
					.message("Please continue using the options above, or open Messages after your interview.")
					.type("INFO")
					.options(null)
					.data(objectMapper.createObjectNode())
					.build();
		};

		messageService.saveMessage(userId, ctx, SENDER_BOT, toJsonContent(response), TYPE_TEXT);
		log.info("Post-interview step (simulated): userId={} interviewId={} type={}", userId, interviewId, response.getType());
		return response;
	}

	private PostInterviewResponse handleSectorChoice(FlowState state, String choice) {
		int sec = resolveSectorIndex(choice);
		state.sectorIndex = sec;
		List<SimFormation> forms = formationsForSector(sec);
		ObjectNode data = objectMapper.createObjectNode();
		data.set("formations", formationsArray(forms));

		return PostInterviewResponse.builder()
				.message(formationChoiceMessage(sec))
				.options(forms.stream().map(SimFormation::optionLabel).toList())
				.type("FORMATION_CHOICE")
				.data(data)
				.build();
	}

	private PostInterviewResponse handleFormationChoice(User user, FlowState state, String choice) {
		SimFormation f = findFormationByOptionLabel(choice);
		if (f == null) {
			f = findFormationFuzzy(choice);
		}
		if (f == null) {
			return PostInterviewResponse.builder()
					.message("Could not match that program. Please pick one of the listed options.")
					.type("INFO")
					.data(objectMapper.createObjectNode())
					.build();
		}
		state.formationTitle = f.title();
		state.formationPrice = f.price();
		state.formationProvider = f.provider();
		state.formationDuration = f.duration();
		state.sectorIndex = sectorIndexFromSectorName(f.sector());

		Formation entity = Formation.builder()
				.user(user)
				.sector(f.sector())
				.title(f.title())
				.description(f.description())
				.selectedAt(LocalDateTime.now())
				.recommendation(null)
				.build();
		formationRepository.save(entity);

		return PostInterviewResponse.builder()
				.message("Great choice! Would you like us to handle your enrollment, or would you prefer to do it yourself?")
				.options(List.of("Yes, handle my enrollment", "I will do it myself"))
				.type("INSCRIPTION_CHOICE")
				.data(objectMapper.createObjectNode())
				.build();
	}

	private PostInterviewResponse handleInscriptionChoice(User user, FlowState state, String choice) {
		boolean handle = choice.toLowerCase(Locale.ROOT).contains("yes, handle")
				|| choice.toLowerCase(Locale.ROOT).contains("handle my enrollment");
		boolean self = choice.toLowerCase(Locale.ROOT).contains("myself");

		if (handle) {
			return PostInterviewResponse.builder()
					.message("Perfect! Please confirm your information: Name: " + user.getFirstName() + " " + user.getLastName()
							+ " — Email: " + user.getEmail() + " — Is this correct?")
					.options(List.of("Yes, confirmed", "No, I need to update"))
					.type("INSCRIPTION_CONFIRM")
					.data(objectMapper.createObjectNode())
					.build();
		}
		if (self) {
			ObjectNode data = objectMapper.createObjectNode();
			data.put("provider", state.formationProvider != null ? state.formationProvider : "");
			data.put("title", state.formationTitle != null ? state.formationTitle : "");
			data.put("price", state.formationPrice != null ? state.formationPrice : "");
			return PostInterviewResponse.builder()
					.message("No problem! Here are the enrollment details for your chosen formation. Contact the provider directly to register. "
							+ "Meanwhile, let me show you job opportunities in your sector.")
					.options(List.of("Show me job opportunities"))
					.type("COMPANY_PROPOSAL_TRIGGER")
					.data(data)
					.build();
		}
		return PostInterviewResponse.builder()
				.message("Please choose one of the enrollment options.")
				.type("INFO")
				.data(objectMapper.createObjectNode())
				.build();
	}

	private PostInterviewResponse handleInscriptionConfirm(User user, FlowState state, String choice) {
		boolean yes = choice.toLowerCase(Locale.ROOT).contains("yes, confirmed")
				|| choice.toLowerCase(Locale.ROOT).contains("confirmed");
		if (yes) {
			String formationTitle = state.formationTitle != null ? state.formationTitle : "Formation";
			String formationPrice = state.formationPrice != null ? state.formationPrice : "N/A";
			String formationProvider = state.formationProvider != null ? state.formationProvider : "N/A";
			String formationDuration = state.formationDuration != null ? state.formationDuration : "N/A";
			String userEmail = user.getEmail() != null ? user.getEmail() : "";
			String userName = user.getFirstName() + " " + user.getLastName();
			emailService.sendFormationInscriptionEmail(userName, userEmail, "N/A", formationTitle, formationPrice);
			String msg = "✅ Your enrollment is confirmed!\n\n"
					+ "**Formation Details**\n"
					+ "• Program → " + formationTitle + "\n"
					+ "• Provider → " + formationProvider + "\n"
					+ "• Duration → " + formationDuration + "\n"
					+ "• Price → " + formationPrice + " (covered by your employer)\n\n"
					+ "📧 **Your login credentials have been sent securely to your email:**\n"
					+ "• Email → " + userEmail + "\n\n"
					+ "⚠️ For security reasons, your password is only available in the email we sent you.\n"
					+ "Please check your inbox at " + userEmail + "\n\n"
					+ "Now let's find you a job in your new sector!";
			return PostInterviewResponse.builder()
					.message(msg)
					.options(List.of("Show me job opportunities"))
					.type("COMPANY_PROPOSAL_TRIGGER")
					.data(objectMapper.createObjectNode())
					.build();
		}
		return PostInterviewResponse.builder()
				.message("Please update your profile in Settings, then return to Messages to continue.")
				.type("INFO")
				.options(null)
				.data(objectMapper.createObjectNode())
				.build();
	}

	private PostInterviewResponse handleCompanyProposalTrigger(FlowState state) {
		List<SimCompany> list = companiesForSector(state.sectorIndex);
		ObjectNode data = objectMapper.createObjectNode();
		data.set("companies", companiesArray(list));
		return PostInterviewResponse.builder()
				.message(companyChoiceMessage(state.sectorIndex))
				.options(list.stream().map(SimCompany::optionLabel).toList())
				.type("COMPANY_CHOICE")
				.data(data)
				.build();
	}

	private PostInterviewResponse handleCompanyChoice(FlowState state, String choice) {
		SimCompany co = findCompanyByOption(state.sectorIndex, choice);
		if (co == null) {
			return PostInterviewResponse.builder()
					.message("Please pick one of the listed companies.")
					.type("INFO")
					.data(objectMapper.createObjectNode())
					.build();
		}
		state.companyName = co.company();
		state.jobTitle = co.job();
		state.companyAddress = co.address();

		ObjectNode data = objectMapper.createObjectNode();
		data.put("company", co.company());
		data.put("job", co.job());
		data.put("salary", co.salary());
		data.put("contact", co.contact());
		data.put("phone", co.phone());
		data.put("address", co.address());

		return PostInterviewResponse.builder()
				.message("Great choice! Would you like us to send your application to " + co.company() + " on your behalf, "
						+ "or would you prefer to contact them directly?")
				.options(List.of("Send my application", "I will apply myself"))
				.type("APPLICATION_CHOICE")
				.data(data)
				.build();
	}

	private PostInterviewResponse handleApplicationChoice(Long userId, User user, FlowState state, String choice) {
		String lc = choice.toLowerCase(Locale.ROOT);
		boolean send = lc.contains("send my application");
		boolean self = lc.contains("apply myself") || lc.contains("i will apply myself");

		if (send) {
			String company = state.companyName != null ? state.companyName : "Company";
			String job = state.jobTitle != null ? state.jobTitle : "Role";
			String formation = state.formationTitle != null ? state.formationTitle : "N/A";
			String name = user.getFirstName() + " " + user.getLastName();
			emailService.sendCandidatureEmail(userId, name, user.getEmail(), company, job, FIXED_SKILLS, formation);
			String applicationMsg = "📨 Your application has been sent to **" + company + "**!\n\n"
					+ "**Application Details**\n"
					+ "• Company → " + company + "\n"
					+ "• Position → " + job + "\n"
					+ "• Status → Pending review\n\n"
					+ "**Company Contact**\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n\n"
					+ "We will notify you as soon as we receive a response. This usually takes 24-48 hours.";
			return PostInterviewResponse.builder()
					.message(applicationMsg)
					.options(null)
					.type("APPLICATION_SENT")
					.data(objectMapper.createObjectNode())
					.build();
		}
		if (self) {
			String company = state.companyName != null ? state.companyName : "the company";
			String address = state.companyAddress != null ? state.companyAddress : "";
			return PostInterviewResponse.builder()
					.message("Here are the complete contact details for " + company + ": Email: imgharnenourddine3@gmail.com — Phone: 0641177339 — Address: "
							+ address + ". Good luck with your application! If you need help, our support team is available.")
					.options(null)
					.type("INFO")
					.data(objectMapper.createObjectNode())
					.build();
		}
		return PostInterviewResponse.builder()
					.message("Please choose how you would like to proceed with your application.")
					.type("INFO")
					.data(objectMapper.createObjectNode())
					.build();
	}

	private ArrayNode formationsArray(List<SimFormation> forms) {
		ArrayNode arr = objectMapper.createArrayNode();
		for (SimFormation f : forms) {
			ObjectNode o = objectMapper.createObjectNode();
			o.put("title", f.title());
			o.put("duration", f.duration());
			o.put("price", f.price());
			o.put("description", f.description());
			o.put("provider", f.provider());
			arr.add(o);
		}
		return arr;
	}

	private ArrayNode companiesArray(List<SimCompany> list) {
		ArrayNode arr = objectMapper.createArrayNode();
		for (SimCompany c : list) {
			ObjectNode o = objectMapper.createObjectNode();
			o.put("company", c.company());
			o.put("job", c.job());
			o.put("salary", c.salary());
			o.put("contact", c.contact());
			o.put("phone", c.phone());
			o.put("address", c.address());
			arr.add(o);
		}
		return arr;
	}

	private static int resolveSectorIndex(String choice) {
		if (choice == null || choice.isBlank()) {
			return 1;
		}
		String ch = choice.toLowerCase(Locale.ROOT);
		if (ch.contains("industrial") || ch.contains("maintenance") || ch.contains("automation")) {
			return 1;
		}
		if (ch.contains("logistics") || ch.contains("supply chain")) {
			return 2;
		}
		if (ch.contains("healthcare") || ch.contains("health")) {
			return 3;
		}
		return 1;
	}

	private static List<SimFormation> formationsForSector(int sec) {
		return switch (sec) {
			case 2 -> FORMATIONS_S2;
			case 3 -> FORMATIONS_S3;
			default -> FORMATIONS_S1;
		};
	}

	private static List<SimCompany> companiesForSector(int sec) {
		return switch (sec) {
			case 2 -> COMPANIES_S2;
			case 3 -> COMPANIES_S3;
			default -> COMPANIES_S1;
		};
	}

	private static int sectorIndexFromSectorName(String sector) {
		if (SECTOR_2.equalsIgnoreCase(sector)) {
			return 2;
		}
		if (SECTOR_3.equalsIgnoreCase(sector)) {
			return 3;
		}
		return 1;
	}

	private SimFormation findFormationByOptionLabel(String choice) {
		if (choice == null) {
			return null;
		}
		for (SimFormation f : FORMATIONS_S1) {
			if (f.optionLabel().equalsIgnoreCase(choice.trim())) {
				return f;
			}
		}
		for (SimFormation f : FORMATIONS_S2) {
			if (f.optionLabel().equalsIgnoreCase(choice.trim())) {
				return f;
			}
		}
		for (SimFormation f : FORMATIONS_S3) {
			if (f.optionLabel().equalsIgnoreCase(choice.trim())) {
				return f;
			}
		}
		return null;
	}

	private SimFormation findFormationFuzzy(String choice) {
		if (choice == null || choice.isBlank()) {
			return null;
		}
		String ch = choice.toLowerCase(Locale.ROOT);
		List<SimFormation> all = new ArrayList<>(FORMATIONS_S1);
		all.addAll(FORMATIONS_S2);
		all.addAll(FORMATIONS_S3);
		for (SimFormation f : all) {
			if (ch.contains(f.optionLabel().toLowerCase(Locale.ROOT).substring(0, Math.min(8, f.optionLabel().length())))) {
				return f;
			}
		}
		for (SimFormation f : all) {
			String t = f.title().toLowerCase(Locale.ROOT);
			if (ch.length() >= 4 && t.contains(ch.substring(0, Math.min(ch.length(), 12)))) {
				return f;
			}
		}
		if (ch.contains("cnc")) {
			return FORMATIONS_S1.get(0);
		}
		if (ch.contains("plc") || ch.contains("industrial automation")) {
			return FORMATIONS_S1.get(1);
		}
		if (ch.contains("preventive")) {
			return FORMATIONS_S1.get(2);
		}
		if (ch.contains("warehouse")) {
			return FORMATIONS_S2.get(0);
		}
		if (ch.contains("supply chain")) {
			return FORMATIONS_S2.get(1);
		}
		if (ch.contains("transport") && ch.contains("distribution")) {
			return FORMATIONS_S2.get(2);
		}
		if (ch.contains("biomedical")) {
			return FORMATIONS_S3.get(0);
		}
		if (ch.contains("medical it") || ch.contains("ehr")) {
			return FORMATIONS_S3.get(1);
		}
		if (ch.contains("sterilization")) {
			return FORMATIONS_S3.get(2);
		}
		return null;
	}

	private static SimCompany findCompanyByOption(int sectorIndex, String choice) {
		if (choice == null) {
			return null;
		}
		String trimmed = choice.trim();
		for (SimCompany c : companiesForSector(sectorIndex)) {
			if (c.optionLabel().equalsIgnoreCase(trimmed) || trimmed.contains(c.company().split("—")[0].trim())) {
				return c;
			}
		}
		String ch = choice.toLowerCase(Locale.ROOT);
		for (SimCompany c : companiesForSector(sectorIndex)) {
			if (ch.contains("renault") && c.company().toLowerCase(Locale.ROOT).contains("renault")) {
				return c;
			}
			if (ch.contains("leoni") && c.company().toLowerCase(Locale.ROOT).contains("leoni")) {
				return c;
			}
			if (ch.contains("ocp") && c.company().toLowerCase(Locale.ROOT).contains("ocp")) {
				return c;
			}
			if (ch.contains("marsa") && c.company().toLowerCase(Locale.ROOT).contains("marsa")) {
				return c;
			}
			if (ch.contains("dhl") && c.company().toLowerCase(Locale.ROOT).contains("dhl")) {
				return c;
			}
			if (ch.contains("décathlon") || ch.contains("decathlon")) {
				if (c.company().toLowerCase(Locale.ROOT).contains("décathlon") || c.company().toLowerCase(Locale.ROOT).contains("decathlon")) {
					return c;
				}
			}
			if (ch.contains("clinique") && c.company().toLowerCase(Locale.ROOT).contains("clinique")) {
				return c;
			}
			if (ch.contains("chu") || ch.contains("ibn rochd")) {
				if (c.company().toLowerCase(Locale.ROOT).contains("chu")) {
					return c;
				}
			}
			if (ch.contains("sanofi")) {
				if (c.company().toLowerCase(Locale.ROOT).contains("sanofi")) {
					return c;
				}
			}
		}
		return null;
	}

	private String toJsonContent(PostInterviewResponse r) {
		try {
			ObjectNode root = objectMapper.createObjectNode();
			root.put("message", r.getMessage() != null ? r.getMessage() : "");
			if (r.getOptions() == null) {
				root.putNull("options");
			} else {
				ArrayNode opts = root.putArray("options");
				for (String o : r.getOptions()) {
					opts.add(o);
				}
			}
			root.put("type", r.getType() != null ? r.getType() : "INFO");
			if (r.getData() != null && !r.getData().isNull()) {
				root.set("data", r.getData());
			} else {
				root.set("data", objectMapper.createObjectNode());
			}
			return objectMapper.writeValueAsString(root);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize post-interview response", e);
		}
	}

	private static String flowKey(Long userId, Long interviewId) {
		return userId + ":" + interviewId;
	}

	public static String postInterviewContext(Long interviewId) {
		return "POST_INTERVIEW_" + interviewId;
	}

	private record SimFormation(
			String sector,
			String title,
			String duration,
			String price,
			String description,
			String provider,
			String optionLabel
	) {}

	private record SimCompany(
			String company,
			String job,
			String salary,
			String contact,
			String phone,
			String address,
			String optionLabel
	) {}

	private static final class FlowState {
		private int sectorIndex = 1;
		private String formationTitle;
		private String formationPrice;
		private String formationProvider;
		private String formationDuration;
		private String companyName;
		private String jobTitle;
		private String companyAddress;
	}

	private static String formationChoiceMessage(int sec) {
		return switch (sec) {
			case 2 -> "🎓 Here are 3 training programs for **Logistics & Supply Chain Management**:\n\n"
					+ "---\n"
					+ "**1. Warehouse Management & WMS Systems**\n"
					+ "• Duration → 2 months\n"
					+ "• Price → 3,200 MAD\n"
					+ "• Provider → Logistics Training Center — Casablanca Port Zone\n"
					+ "• Description → Master warehouse operations, inventory management and WMS systems (SAP WM, Manhattan). CILT certification prep.\n\n"
					+ "---\n"
					+ "**2. Supply Chain Operations Coordinator**\n"
					+ "• Duration → 4 months\n"
					+ "• Price → 6,500 MAD\n"
					+ "• Provider → ISCAE Casablanca — Formation Continue\n"
					+ "• Description → End-to-end supply chain management including procurement, transport coordination and ERP systems. Internship guaranteed.\n\n"
					+ "---\n"
					+ "**3. Transport & Distribution Manager**\n"
					+ "• Duration → 3 months\n"
					+ "• Price → 4,800 MAD\n"
					+ "• Provider → Institut Supérieur de Transport et de Logistique — Rabat\n"
					+ "• Description → Fleet management, route optimization, customs procedures and TMS software training.\n\n"
					+ "Which training program would you like to enroll in?";
			case 3 -> "🎓 Here are 3 training programs for **Healthcare Technology Support**:\n\n"
					+ "---\n"
					+ "**1. Biomedical Equipment Technician**\n"
					+ "• Duration → 6 months\n"
					+ "• Price → 9,500 MAD\n"
					+ "• Provider → Institut Supérieur des Professions Infirmières — Casablanca\n"
					+ "• Description → Maintenance and repair of medical devices including imaging systems and patient monitors. Hospital internship included.\n\n"
					+ "---\n"
					+ "**2. Medical IT Support Specialist**\n"
					+ "• Duration → 3 months\n"
					+ "• Price → 5,200 MAD\n"
					+ "• Provider → OFPPT — Secteur Santé Casablanca\n"
					+ "• Description → IT infrastructure in healthcare, Electronic Health Records (EHR), PACS/RIS systems and DICOM standards.\n\n"
					+ "---\n"
					+ "**3. Sterilization & Medical Device Processing**\n"
					+ "• Duration → 2 months\n"
					+ "• Price → 2,800 MAD\n"
					+ "• Provider → Centre Hospitalier Ibn Rochd — Formation Continue\n"
					+ "• Description → ISO 13485 standards for medical device sterilization and quality control. Immediate job placement in clinics.\n\n"
					+ "Which training program would you like to enroll in?";
			default -> "🎓 Here are 3 training programs for **Industrial Maintenance & Automation**:\n\n"
					+ "---\n"
					+ "**1. CNC Machine Technician Certification**\n"
					+ "• Duration → 3 months\n"
					+ "• Price → 4,500 MAD\n"
					+ "• Provider → OFPPT Casablanca — Institut Spécialisé de Technologie Appliquée\n"
					+ "• Description → Learn to operate, program and maintain CNC machines. Includes hands-on training with Fanuc and Siemens controllers.\n\n"
					+ "---\n"
					+ "**2. Industrial Automation & PLC Programming**\n"
					+ "• Duration → 6 months\n"
					+ "• Price → 8,200 MAD\n"
					+ "• Provider → Centre de Formation Professionnelle ISTA Ain Sebaa\n"
					+ "• Description → Master Programmable Logic Controllers (PLC), SCADA systems and industrial robotics. Siemens S7-300/400 certification included.\n\n"
					+ "---\n"
					+ "**3. Preventive Maintenance Technician**\n"
					+ "• Duration → 4 months\n"
					+ "• Price → 5,800 MAD\n"
					+ "• Provider → OFPPT Mohammedia — Secteur Industrie\n"
					+ "• Description → Comprehensive training in mechanical, electrical and hydraulic maintenance. Job placement assistance included.\n\n"
					+ "Which training program would you like to enroll in?";
		};
	}

	private static String companyChoiceMessage(int sec) {
		return switch (sec) {
			case 2 -> "🏢 Here are 3 companies hiring in **Logistics & Supply Chain Management**:\n\n"
					+ "---\n"
					+ "**1. Marsa Maroc — Port de Casablanca**\n"
					+ "• Position → Logistics Operations Coordinator\n"
					+ "• Salary → 5,000 — 6,500 MAD/month\n"
					+ "• Address → Port de Casablanca, Casablanca\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n"
					+ "• About → National port operator, career growth in maritime logistics\n\n"
					+ "---\n"
					+ "**2. DHL Maroc**\n"
					+ "• Position → Warehouse Supervisor\n"
					+ "• Salary → 5,500 — 7,000 MAD/month\n"
					+ "• Address → Zone Industrielle Ouled Salah, Casablanca\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n"
					+ "• About → Global express logistics, modern facilities, training programs\n\n"
					+ "---\n"
					+ "**3. Décathlon Maroc — Distribution Center**\n"
					+ "• Position → Supply Chain Operator\n"
					+ "• Salary → 4,500 — 6,000 MAD/month\n"
					+ "• Address → Bouskoura Industrial Park, Casablanca\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n"
					+ "• About → Sports retail distribution, fast-paced warehouse environment\n\n"
					+ "Which company would you like to apply to?";
			case 3 -> "🏢 Here are 3 companies hiring in **Healthcare Technology Support**:\n\n"
					+ "---\n"
					+ "**1. Clinique Internationale de Casablanca**\n"
					+ "• Position → Biomedical Equipment Technician\n"
					+ "• Salary → 5,500 — 7,500 MAD/month\n"
					+ "• Address → Boulevard Ghandi, Casablanca\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n"
					+ "• About → Private hospital network, advanced medical equipment\n\n"
					+ "---\n"
					+ "**2. CHU Ibn Rochd — Casablanca**\n"
					+ "• Position → Medical Device Maintenance Specialist\n"
					+ "• Salary → 5,000 — 6,500 MAD/month\n"
					+ "• Address → Rue des Hôpitaux, Casablanca\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n"
					+ "• About → Major public hospital, diverse clinical environments\n\n"
					+ "---\n"
					+ "**3. Sanofi Maroc**\n"
					+ "• Position → Laboratory Equipment Technician\n"
					+ "• Salary → 6,500 — 9,000 MAD/month\n"
					+ "• Address → Zone Industrielle Sidi Bernoussi, Casablanca\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n"
					+ "• About → Global pharmaceutical leader, lab and production equipment\n\n"
					+ "Which company would you like to apply to?";
			default -> "🏢 Here are 3 companies hiring in **Industrial Maintenance & Automation**:\n\n"
					+ "---\n"
					+ "**1. Renault Maroc — Usine de Casablanca**\n"
					+ "• Position → Industrial Maintenance Technician\n"
					+ "• Salary → 5,500 — 7,000 MAD/month\n"
					+ "• Address → Route de Rabat, Ain Sebaa, Casablanca\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n"
					+ "• About → Leading automotive manufacturer, stable employment, full benefits package\n\n"
					+ "---\n"
					+ "**2. Leoni Wire — Tanger**\n"
					+ "• Position → Automation Maintenance Engineer\n"
					+ "• Salary → 6,000 — 8,500 MAD/month\n"
					+ "• Address → Zone Franche de Tanger, Tanger\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n"
					+ "• About → International cable manufacturer, modern facilities, career growth opportunities\n\n"
					+ "---\n"
					+ "**3. OCP Group — Jorf Lasfar**\n"
					+ "• Position → Mechanical Maintenance Specialist\n"
					+ "• Salary → 7,000 — 10,000 MAD/month\n"
					+ "• Address → Jorf Lasfar Industrial Zone, El Jadida\n"
					+ "• Email → imgharnenourddine3@gmail.com\n"
					+ "• Phone → 0641177339\n"
					+ "• About → Morocco's leading phosphate group, excellent compensation, housing benefits\n\n"
					+ "Which company would you like to apply to?";
		};
	}
}
