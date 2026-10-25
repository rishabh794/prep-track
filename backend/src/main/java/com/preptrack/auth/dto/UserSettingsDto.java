package com.preptrack.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsDto {
    private Integer dailyTimeBudgetMin;
    private Integer maxNewProblemsDay;
    private Integer maxRevisionsDay;
    private Double targetSkillLevel;
    private Boolean onboardingComplete;
}
