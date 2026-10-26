package com.preptrack.revision.service;

import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.entity.FollowUpType;
import com.preptrack.revision.entity.RevisionCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RevisionTimeEstimatorTest {

    private final RevisionTimeEstimator estimator = new RevisionTimeEstimator();

    @Test
    @DisplayName("Recall-only revision returns 5 minutes")
    void testRecallOnlyReturnsFiveMinutes() {
        Problem problem = Problem.builder().estimatedMinutes(30).build();
        RevisionCard card = RevisionCard.builder().problem(problem).requiredFollowUp(null).build();

        int minutes = estimator.estimateMinutes(card);
        assertThat(minutes).isEqualTo(5);
    }

    @Test
    @DisplayName("Code from scratch returns full estimated minutes")
    void testCodeFromScratchReturnsFullTime() {
        Problem problem = Problem.builder().estimatedMinutes(30).build();
        RevisionCard card = RevisionCard.builder()
                .problem(problem)
                .requiredFollowUp(FollowUpType.CODE_FROM_SCRATCH)
                .build();

        int minutes = estimator.estimateMinutes(card);
        assertThat(minutes).isEqualTo(30);
    }

    @Test
    @DisplayName("Edge case list returns half time with 10 min floor")
    void testEdgeCaseListReturnsHalfTime() {
        Problem problem30 = Problem.builder().estimatedMinutes(30).build();
        RevisionCard card30 = RevisionCard.builder()
                .problem(problem30)
                .requiredFollowUp(FollowUpType.EDGE_CASE_LIST)
                .build();
        assertThat(estimator.estimateMinutes(card30)).isEqualTo(15);

        Problem problem15 = Problem.builder().estimatedMinutes(15).build();
        RevisionCard card15 = RevisionCard.builder()
                .problem(problem15)
                .requiredFollowUp(FollowUpType.EDGE_CASE_LIST)
                .build();
        assertThat(estimator.estimateMinutes(card15)).isEqualTo(10);
    }
}
