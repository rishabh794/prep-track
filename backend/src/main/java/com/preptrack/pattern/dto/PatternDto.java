package com.preptrack.pattern.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternDto {
    private Long id;
    private String name;
    private String slug;
    private String parentTopic;
    private String description;
    private Integer difficultyTier;
}
