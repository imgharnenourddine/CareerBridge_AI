package com.ai.guild.career_transition_platform.repository;

import com.ai.guild.career_transition_platform.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

	List<Interview> findByUser_IdOrderByCreatedAtDesc(Long userId);

	Optional<Interview> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

	Optional<Interview> findFirstByUser_IdAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long userId);

	Optional<Interview> findFirstByUser_IdAndCompletedAtIsNullOrderByStartedAtDesc(Long userId);

	Optional<Interview> findFirstByUser_IdAndCompletedAtIsNullOrderByCreatedAtDesc(Long userId);

	Optional<Interview> findByIdAndUser_Id(Long id, Long userId);

	long countByUser_IdAndCompletedAtIsNotNull(Long userId);
}
