package com.ai.guild.career_transition_platform.repository;

import com.ai.guild.career_transition_platform.entity.Formation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormationRepository extends JpaRepository<Formation, Long> {

	List<Formation> findByUser_Id(Long userId);

	List<Formation> findByRecommendation_Id(Long recommendationId);
}
