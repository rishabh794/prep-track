package com.preptrack.attempt.dto;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.attempt.entity.FailureReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAttemptRequest {

    @NotNull(message = "problemId is required")
    private Long problemId;

    @NotNull(message = "outcome is required")
    private AttemptOutcome outcome;

    private FailureReason failureReason;

    private Integer timeTakenSec;

    @Builder.Default
    private Integer hintsUsed = 0;

    @Builder.Default
    private Boolean isRevision = false;

    private String notes;

    private String insightNote;
}
