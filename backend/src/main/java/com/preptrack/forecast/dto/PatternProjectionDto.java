package com.preptrack.forecast.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternProjectionDto {
    private String pattern;
    private Double currentSkill;
    private Double projectedSkill;
    private Double currentRetention;
    private Double projectedRetention;
    private Double companyRelevance;
    private TriageStatus status;
}
