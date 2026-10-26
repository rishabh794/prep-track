package com.preptrack.revision.dto;

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
public class SubmitRecallRequest {
    @NotBlank(message = "userApproach cannot be blank")
    @Size(max = 2000, message = "userApproach cannot exceed 2000 characters")
    private String userApproach;
}
