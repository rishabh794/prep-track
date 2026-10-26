package com.preptrack.revision.dto;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.revision.entity.FollowUpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionCardDto {
    private Long id;
    private Long problemId;
    private String problemTitle;
    private String difficulty;
    private Integer estimatedMinutes;
    private LocalDate nextReviewDate;
    private Integer reviewCount;
    private AttemptOutcome lastOutcome;
    private LocalDateTime lastReviewedAt;
    private FollowUpType requiredFollowUp;
}
