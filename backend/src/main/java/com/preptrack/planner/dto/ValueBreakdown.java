package com.preptrack.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValueBreakdown {

    @Builder.Default
    private List<PatternContribution> patterns = new ArrayList<>();

    @Builder.Default
    private Double interviewRelevance = 1.0;

    @Builder.Default
    private Double retryBoost = 1.0;

    @Builder.Default
    private Double followUpMultiplier = 1.0;

    @Builder.Default
    private Double diversityPenalty = 1.0;

    @Builder.Default
    private Double totalValue = 0.0;

    public PatternContribution getTopContributor() {
        if (patterns == null || patterns.isEmpty()) {
            return PatternContribution.builder()
                    .patternName("General Problem")
                    .skill(0.0)
                    .need(1.0)
                    .retention(0.0)
                    .contribution(0.0)
                    .build();
        }
        return patterns.stream()
                .max(Comparator.comparingDouble(p -> p.getContribution() != null ? p.getContribution() : 0.0))
                .orElse(patterns.get(0));
    }
}
