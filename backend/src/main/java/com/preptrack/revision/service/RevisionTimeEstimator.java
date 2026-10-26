package com.preptrack.revision.service;

import com.preptrack.revision.entity.FollowUpType;
import com.preptrack.revision.entity.RevisionCard;
import org.springframework.stereotype.Component;

@Component
public class RevisionTimeEstimator {

    /**
     * Estimates revision time based on follow-up type.
     * Recall-only revisions take ~5 minutes.
     * Implementation / timed retries take full problem time.
     * Edge case revisions take half time (min 10 min).
     */
    public int estimateMinutes(RevisionCard card) {
        if (card == null || card.getProblem() == null) {
            return 5;
        }

        FollowUpType followUp = card.getRequiredFollowUp();
        int fullMinutes = card.getProblem().getEstimatedMinutes() != null ? card.getProblem().getEstimatedMinutes() : 30;

        if (followUp == null) {
            return 5; // recall-only revision
        }

        return switch (followUp) {
            case CODE_FROM_SCRATCH, TIMED_RETRY -> fullMinutes;
            case EDGE_CASE_LIST -> Math.max(10, fullMinutes / 2);
            case EASIER_PREREQUISITE -> fullMinutes;
        };
    }
}
