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
    private Double overallReadiness;
    private List<PatternProjectionDto> patternProjections;
    private List<String> recommendations;
}
