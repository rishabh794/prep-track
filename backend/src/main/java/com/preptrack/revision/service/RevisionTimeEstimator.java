package com.preptrack.revision.service;

import com.preptrack.revision.entity.FollowUpType;
import com.preptrack.revision.entity.RevisionCard;
import com.preptrack.config.EngineProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RevisionTimeEstimator {
    private final EngineProperties policy;

    /**
     * Estimates revision time based on follow-up type.
     * Recall-only revisions take ~5 minutes.
     * Implementation / timed retries take full problem time.
     * Edge case revisions take half time (min 10 min).
     */
    public int estimateMinutes(RevisionCard card) {
        if (card == null || card.getProblem() == null) {
            return policy.getRecallMinutes();
        }

        FollowUpType followUp = card.getRequiredFollowUp();
        int fullMinutes = card.getProblem().getEstimatedMinutes() != null ? card.getProblem().getEstimatedMinutes() : policy.getDefaultProblemMinutes();

        if (followUp == null) {
            return policy.getRecallMinutes();
        }

        return switch (followUp) {
            case CODE_FROM_SCRATCH, TIMED_RETRY -> fullMinutes;
            case EDGE_CASE_LIST -> Math.max(policy.getEdgeCaseMinimumMinutes(), (int) (fullMinutes * policy.getEdgeCaseTimeFraction()));
            case EASIER_PREREQUISITE -> fullMinutes;
        };
    }
}
