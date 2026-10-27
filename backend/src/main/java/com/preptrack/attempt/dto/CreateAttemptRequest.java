package com.preptrack.attempt.dto;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.attempt.entity.FailureReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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

    @PositiveOrZero
    private Integer timeTakenSec;

    @Builder.Default
    @PositiveOrZero
    private Integer hintsUsed = 0;

    @Builder.Default
    private Boolean isRevision = false;

    @Size(max = 20000)
    private String notes;

    @Size(max = 20000)
    private String insightNote;
}
