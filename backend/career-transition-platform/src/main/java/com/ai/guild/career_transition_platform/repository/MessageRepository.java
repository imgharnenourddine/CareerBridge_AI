package com.ai.guild.career_transition_platform.repository;

import com.ai.guild.career_transition_platform.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

	List<Message> findByUser_IdAndContextOrderByOrderIndexAsc(Long userId, String context);

	@Query("""
			SELECT COALESCE(MAX(m.orderIndex), -1)
			FROM Message m
			WHERE m.user.id = :userId AND m.context = :context
			""")
	int findMaxOrderIndexByUserIdAndContext(@Param("userId") Long userId, @Param("context") String context);
}
