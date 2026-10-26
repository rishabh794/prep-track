package com.preptrack.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedPatternDto {
    private Long patternId;
    private String patternSlug;
    private String patternName;
    private boolean wasWeak;
    private double confidence;
}
