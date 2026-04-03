package com.ai.guild.career_transition_platform.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostInterviewResponse {

	private String message;
	private List<String> options;
	private String type;
	private JsonNode data;
}
