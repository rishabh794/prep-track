package com.preptrack.revision.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRevisionRequest {
    private Long recallCheckId;
    private String outcome;
    private String userOverride;
    private String overrideGrade;
}
