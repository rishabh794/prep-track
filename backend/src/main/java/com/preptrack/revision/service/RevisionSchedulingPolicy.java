package com.preptrack.revision.service;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.config.EngineProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RevisionSchedulingPolicy {
    private final EngineProperties policy;

    public int intervalDays(AttemptOutcome outcome, int successfulReviews, boolean firstAttempt, double stability) {
        if (outcome != AttemptOutcome.SOLVED_INDEPENDENTLY) return policy.getRevisionRetryDays();
        if (firstAttempt) return policy.revisionIntervalDays(1);
        int stabilityDays = (int) Math.max(1, Math.round(-Math.log(policy.getRevisionRetentionTarget()) * stability));
        return Math.max(1, Math.min(policy.revisionIntervalDays(successfulReviews), stabilityDays));
    }
}
