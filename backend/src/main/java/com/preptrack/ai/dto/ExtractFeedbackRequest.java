package com.preptrack.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractFeedbackRequest {
    @NotBlank(message = "rawFeedback cannot be blank")
    @Size(max = 5000, message = "rawFeedback cannot exceed 5000 characters")
    private String rawFeedback;
}
