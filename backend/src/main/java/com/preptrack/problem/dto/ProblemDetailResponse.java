package com.preptrack.problem.dto;

import com.preptrack.pattern.dto.PatternSummaryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemDetailResponse {
    private Long id;
    private String canonicalTitle;
    private String difficulty;
    private String referenceApproach;
    private String optimalComplexity;
    private List<PatternSummaryDto> patterns;
    private List<ProblemSourceDto> sources;
    private List<ProblemRelationDto> relations;
    private String insightNote;
}
