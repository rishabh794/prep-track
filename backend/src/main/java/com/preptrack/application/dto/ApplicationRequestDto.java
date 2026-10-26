package com.preptrack.application.dto;

import com.preptrack.application.entity.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationRequestDto {

    @NotBlank(message = "Company name is required")
    private String companyName;

    private String role;

    private ApplicationStatus status;

    private LocalDate appliedDate;

    private String notes;
}
