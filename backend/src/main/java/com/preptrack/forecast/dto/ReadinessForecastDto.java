package com.preptrack.forecast.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadinessForecastDto {
    private Long interviewId;
    private LocalDate interviewDate;
    private String companyName;
    private Long daysRemaining;
    private ForecastStatus status;
    private String statusMessage;
    private Integer dailyBudgetMinutes;
    private Integer maxNewProblemsDay;
    private Integer maxRevisionsDay;
    private Double targetSkillLevel;
    private Integer simulationDays;
    private LocalDate asOfDate;
    private LocalDate projectedThroughDate;
    private Boolean horizonCapped;
    private String model;
    private String modelVersion;
    private String policyVersion;
    private List<String> limitations;
    private Double overallReadiness;
    private List<PatternProjectionDto> patternProjections;
    private List<String> recommendations;
}
