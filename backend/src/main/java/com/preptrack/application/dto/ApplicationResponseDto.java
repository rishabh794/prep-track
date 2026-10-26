package com.preptrack.application.dto;

import com.preptrack.application.entity.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponseDto {

    private Long id;
    private String companyName;
    private String role;
    private ApplicationStatus status;
    private LocalDate appliedDate;
    private String notes;
    @Builder.Default
    private List<InterviewResponseDto> interviews = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
