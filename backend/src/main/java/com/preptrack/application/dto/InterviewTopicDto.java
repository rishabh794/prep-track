package com.preptrack.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewTopicDto {
    private Long id;
    private Long patternId;
    private String patternName;
    private String patternSlug;
    private Boolean wasWeak;
    private Boolean confirmed;
}
