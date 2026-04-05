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
public class DashboardStatsResponse {

	private long interviewsCount;
	private String interviewStatus;
	private LocalDateTime interviewCompletedAt;
	private String formationStatus;
	private String formationTitle;
	private int candidaturesCount;
	private int pendingCandidatures;
	private int acceptedCandidatures;
	private int rejectedCandidatures;
	private String guildRank;
	private int guildProgressPercent;
}
