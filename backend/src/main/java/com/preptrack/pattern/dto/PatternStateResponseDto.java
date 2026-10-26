package com.preptrack.pattern.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternStateResponseDto {
    private Long patternId;
    private String name;
    private String slug;
    private String parentTopic;
    private Double skill;
    private Double retention;
    private Double stability;
    private LocalDateTime lastPracticedAt;
    private Integer totalAttempts;
    private Integer independentSolves;
}
