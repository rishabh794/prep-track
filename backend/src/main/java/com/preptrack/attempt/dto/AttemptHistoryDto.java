package com.preptrack.attempt.dto;

import com.preptrack.attempt.entity.*;
import com.preptrack.revision.entity.FollowUpType;
import java.time.Instant;

public record AttemptHistoryDto(Long id, Long problemId, String problemTitle, AttemptActivityType activityType,
        AttemptOutcome outcome, FailureReason failureReason, Integer timeTakenSec, Integer hintsUsed,
        Boolean isRevision, Long recallCheckId, FollowUpType followUpType, String notes, Instant attemptedAt) {}
