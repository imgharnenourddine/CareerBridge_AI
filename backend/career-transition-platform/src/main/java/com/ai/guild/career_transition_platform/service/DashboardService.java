package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.dto.DashboardStatsResponse;
import com.ai.guild.career_transition_platform.entity.Candidature;
import com.ai.guild.career_transition_platform.entity.Formation;
import com.ai.guild.career_transition_platform.entity.Interview;
import com.ai.guild.career_transition_platform.enums.CandidatureStatus;
import com.ai.guild.career_transition_platform.repository.CandidatureRepository;
import com.ai.guild.career_transition_platform.repository.FormationRepository;
import com.ai.guild.career_transition_platform.repository.InterviewRepository;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

	private static final String INTERVIEW_NOT_STARTED = "NOT_STARTED";
	private static final String INTERVIEW_IN_PROGRESS = "IN_PROGRESS";
	private static final String INTERVIEW_COMPLETED = "COMPLETED";

	private static final String FORMATION_NOT_SELECTED = "NOT_SELECTED";
	private static final String FORMATION_IN_PROGRESS = "IN_PROGRESS";
	private static final String FORMATION_COMPLETED = "COMPLETED";

	private static final String RANK_APPRENTI = "APPRENTI";
	private static final String RANK_ARTISAN = "ARTISAN";
	private static final String RANK_COMPAGNON = "COMPAGNON";
	private static final String RANK_MAITRE = "MAITRE";

	private final InterviewRepository interviewRepository;
	private final FormationRepository formationRepository;
	private final CandidatureRepository candidatureRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public DashboardStatsResponse getStats(Long userId) {
		if (!userRepository.existsById(userId)) {
			log.warn("Dashboard stats: user not found userId={}", userId);
			throw new EntityNotFoundException("User not found: " + userId);
		}

		Optional<Interview> incomplete = interviewRepository.findFirstByUser_IdAndCompletedAtIsNullOrderByStartedAtDesc(userId);
		Optional<Interview> latestCompleted = interviewRepository.findFirstByUser_IdAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId);

		long interviewsCount = interviewRepository.countByUser_IdAndCompletedAtIsNotNull(userId);

		String interviewStatus;
		LocalDateTime interviewCompletedAt = null;
		if (incomplete.isPresent()) {
			interviewStatus = INTERVIEW_IN_PROGRESS;
		} else if (latestCompleted.isPresent()) {
			interviewStatus = INTERVIEW_COMPLETED;
			interviewCompletedAt = latestCompleted.get().getCompletedAt();
		} else {
			interviewStatus = INTERVIEW_NOT_STARTED;
		}

		List<Candidature> candidatures = candidatureRepository.findByUser_Id(userId);
		int candidaturesCount = candidatures.size();
		long pending = candidatures.stream().filter(c -> c.getStatus() == CandidatureStatus.PENDING).count();
		long accepted = candidatures.stream().filter(c -> c.getStatus() == CandidatureStatus.ACCEPTED).count();
		long rejected = candidatures.stream().filter(c -> c.getStatus() == CandidatureStatus.REJECTED).count();

		log.debug("Dashboard counts userId={} total={} pending={} accepted={} rejected={}",
				userId, candidaturesCount, pending, accepted, rejected);

		List<Formation> formations = formationRepository.findByUser_Id(userId);
		Optional<Formation> latestFormation = formations.stream()
				.max(Comparator.comparing(Formation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

		String formationStatus;
		String formationTitle = null;
		if (latestFormation.isEmpty()) {
			formationStatus = FORMATION_NOT_SELECTED;
		} else {
			Formation f = latestFormation.get();
			formationTitle = f.getTitle();
			if (f.getSelectedAt() == null) {
				formationStatus = FORMATION_NOT_SELECTED;
			} else if (accepted >= 1) {
				formationStatus = FORMATION_COMPLETED;
			} else {
				formationStatus = FORMATION_IN_PROGRESS;
			}
		}

		boolean interviewCompleted = latestCompleted.isPresent();
		boolean formationSelected = latestFormation.map(f -> f.getSelectedAt() != null).orElse(false);

		String guildRank;
		int guildProgressPercent;
		if (accepted >= 1) {
			guildRank = RANK_MAITRE;
			guildProgressPercent = 100;
		} else if (candidaturesCount >= 1) {
			guildRank = RANK_COMPAGNON;
			guildProgressPercent = 75;
		} else if (formationSelected) {
			guildRank = RANK_ARTISAN;
			guildProgressPercent = 50;
		} else if (interviewCompleted) {
			guildRank = RANK_APPRENTI;
			guildProgressPercent = 25;
		} else {
			guildRank = RANK_APPRENTI;
			guildProgressPercent = 0;
		}

		log.info("Dashboard stats loaded userId={} guildRank={} guildProgressPercent={}", userId, guildRank, guildProgressPercent);
		log.info("Rank calculated userId={} interviewCompleted={} formationSelected={} candidaturesCount={} accepted={}",
				userId, interviewCompleted, formationSelected, candidaturesCount, accepted);

		return DashboardStatsResponse.builder()
				.interviewsCount(interviewsCount)
				.interviewStatus(interviewStatus)
				.interviewCompletedAt(interviewCompletedAt)
				.formationStatus(formationStatus)
				.formationTitle(formationTitle)
				.candidaturesCount(candidaturesCount)
				.pendingCandidatures((int) pending)
				.acceptedCandidatures((int) accepted)
				.rejectedCandidatures((int) rejected)
				.guildRank(guildRank)
				.guildProgressPercent(guildProgressPercent)
				.build();
	}
}
