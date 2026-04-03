package com.ai.guild.career_transition_platform.repository;

import com.ai.guild.career_transition_platform.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

	List<Recommendation> findByUser_Id(Long userId);

	List<Recommendation> findByInterview_Id(Long interviewId);

	List<Recommendation> findByUser_IdOrderByRankOrderAsc(Long userId);
}
