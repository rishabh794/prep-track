package com.preptrack.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingInterviewRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    private String role;

    @NotNull(message = "Interview date is required")
    private LocalDate interviewDate;

    private String notes;
}
