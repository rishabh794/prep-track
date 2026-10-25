package com.preptrack.onboarding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingInterviewResponse {
    private Long applicationId;
    private Long interviewId;
    private String companyName;
    private String role;
    private LocalDate interviewDate;
    private String outcome;
    private String notes;
}
