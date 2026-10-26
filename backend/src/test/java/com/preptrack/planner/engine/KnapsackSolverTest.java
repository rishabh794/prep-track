package com.preptrack.planner.engine;

import com.preptrack.planner.entity.PlanItemType;
import com.preptrack.problem.entity.Problem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnapsackSolverTest {

    private final KnapsackSolver solver = new KnapsackSolver();

    @Test
    @DisplayName("Retries and revisions share max_revisions_day cap")
    void testRetriesShareRevisionCap() {
        List<PlanCandidate> candidates = new ArrayList<>();

        // Add 5 retries (10 min each, value 2.0)
        for (int i = 1; i <= 5; i++) {
            candidates.add(PlanCandidate.builder()
                    .problem(Problem.builder().id((long) i).canonicalTitle("Retry " + i).build())
                    .type(PlanItemType.RETRY)
                    .timeMinutes(10)
                    .value(2.0)
                    .build());
        }

        // Add 10 revisions (5 min each, value 1.5)
        for (int i = 6; i <= 15; i++) {
            candidates.add(PlanCandidate.builder()
                    .problem(Problem.builder().id((long) i).canonicalTitle("Revision " + i).build())
                    .type(PlanItemType.REVISION)
                    .timeMinutes(5)
                    .value(1.5)
                    .build());
        }

        // Budget 120 min, maxReviewSlots = 6, maxNew = 3
        List<PlanCandidate> plan = solver.solve(candidates, 120, 6, 3);

        long reviewCount = plan.stream()
                .filter(c -> c.getType() == PlanItemType.RETRY || c.getType() == PlanItemType.REVISION)
                .count();

        assertThat(reviewCount).isLessThanOrEqualTo(6);
    }

    @Test
    @DisplayName("Budget constraint is strictly respected")
    void testBudgetConstraintRespected() {
        List<PlanCandidate> candidates = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            candidates.add(PlanCandidate.builder()
                    .problem(Problem.builder().id((long) i).canonicalTitle("Prob " + i).build())
                    .type(PlanItemType.NEW_PROBLEM)
                    .timeMinutes(30)
                    .value(1.0 + i)
                    .build());
        }

        // Budget 75 min -> can fit at most two 30-min problems (60 min)
        List<PlanCandidate> plan = solver.solve(candidates, 75, 10, 5);

        int totalTime = plan.stream().mapToInt(PlanCandidate::getTimeMinutes).sum();
        assertThat(totalTime).isLessThanOrEqualTo(75);
        assertThat(plan).hasSize(2);
    }

    @Test
    @DisplayName("Max new problems cap is strictly respected")
    void testMaxNewProblemsCap() {
        List<PlanCandidate> candidates = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            candidates.add(PlanCandidate.builder()
                    .problem(Problem.builder().id((long) i).canonicalTitle("Prob " + i).build())
                    .type(PlanItemType.NEW_PROBLEM)
                    .timeMinutes(10)
                    .value(2.0)
                    .build());
        }

        // Budget 100 min, maxNew = 3
        List<PlanCandidate> plan = solver.solve(candidates, 100, 10, 3);

        long newCount = plan.stream().filter(c -> c.getType() == PlanItemType.NEW_PROBLEM).count();
        assertThat(newCount).isLessThanOrEqualTo(3);
    }
}
