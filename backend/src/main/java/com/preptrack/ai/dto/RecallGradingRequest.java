package com.preptrack.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallGradingRequest {
    private String problemTitle;
    private String descriptionHint;
    private String referenceApproach;
    private String optimalComplexity;
    private String patterns;
    private String insightNote;
    private String userApproach;
}
