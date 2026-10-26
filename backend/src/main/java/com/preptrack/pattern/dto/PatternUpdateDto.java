package com.preptrack.pattern.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternUpdateDto {
    private Long patternId;
    private String patternName;
    private Double skillBefore;
    private Double skillAfter;
    private Double retentionNow;
}
