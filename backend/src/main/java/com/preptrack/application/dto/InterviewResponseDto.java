package com.preptrack.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewResponseDto {
    private Long id;
    private Long applicationId;
    private String companyName;
    private String role;
    private LocalDate interviewDate;
    private String roundName;
    private String outcome;
    private String notes;
    private String rawFeedback;
    private Boolean aiExtracted;
    private List<InterviewTopicDto> topics;
}
