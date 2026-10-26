package com.preptrack.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternContribution {
    private Long patternId;
    private String patternName;
    private Double need;
    private Double retention;
    private Double skill;
    private Double linkWeight;
    private Double contribution;
    @Builder.Default
    private Integer independentSolves = 0;
    @Builder.Default
    private Integer totalInSheets = 0;
}
