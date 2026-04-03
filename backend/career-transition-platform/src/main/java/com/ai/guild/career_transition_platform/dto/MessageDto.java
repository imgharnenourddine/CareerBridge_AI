package com.ai.guild.career_transition_platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDto {

	private Long id;
	private Integer orderIndex;
	private String sender;
	private String type;
	private String content;
	private LocalDateTime createdAt;
}
