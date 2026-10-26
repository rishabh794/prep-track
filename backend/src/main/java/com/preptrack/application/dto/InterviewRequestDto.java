package com.preptrack.application.dto;

import com.preptrack.application.entity.InterviewOutcome;
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
public class InterviewRequestDto {

    @NotNull(message = "Interview date is required")
    private LocalDate interviewDate;

    private String roundName;

    private String notes;

    private InterviewOutcome outcome;
}
