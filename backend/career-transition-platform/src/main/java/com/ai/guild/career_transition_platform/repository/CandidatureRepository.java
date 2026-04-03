package com.ai.guild.career_transition_platform.repository;

import com.ai.guild.career_transition_platform.entity.Candidature;
import com.ai.guild.career_transition_platform.enums.CandidatureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidatureRepository extends JpaRepository<Candidature, Long> {

	List<Candidature> findByUser_Id(Long userId);

	List<Candidature> findByUser_IdAndStatus(Long userId, CandidatureStatus status);
}
