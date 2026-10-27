package com.preptrack.revision.dto;

import com.preptrack.attempt.entity.FailureReason;
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
public class ReviewRevisionRequest {
    private Long recallCheckId;
    private String outcome;
    private String userOverride;
    private String overrideGrade;
    private FailureReason failureReason;
    @PositiveOrZero
    private Integer timeTakenSec;
    @PositiveOrZero
    private Integer hintsUsed;
    @Size(max = 10000)
    private String notes;
}
