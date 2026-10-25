package com.preptrack.problem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemRelationDto {
    private Long relatedProblemId;
    private String relatedProblemTitle;
    private String relationType;
    private String notes;
}
