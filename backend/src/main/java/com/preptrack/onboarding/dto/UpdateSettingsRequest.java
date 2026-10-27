package com.preptrack.onboarding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsRequest {

    @Min(value = 15, message = "Daily budget must be at least 15 minutes")
    @Max(value = 480, message = "Daily budget cannot exceed 480 minutes")
    private Integer dailyTimeBudgetMin;

    @Min(value = 0, message = "Max new problems cannot be negative")
    @Max(value = 20, message = "Max new problems cannot exceed 20")
    private Integer maxNewProblemsDay;

    @Min(value = 0, message = "Max revisions cannot be negative")
    @Max(value = 50, message = "Max revisions cannot exceed 50")
    private Integer maxRevisionsDay;

    @Min(value = 0, message = "Target skill level must be between 0.0 and 1.0")
    @Max(value = 1, message = "Target skill level must be between 0.0 and 1.0")
    private Double targetSkillLevel;

    private String timezone;
}
