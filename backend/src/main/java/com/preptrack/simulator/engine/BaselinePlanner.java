package com.preptrack.simulator.engine;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.simulator.dto.SimulationConfig;
import java.util.*;
import static com.preptrack.simulator.engine.SyntheticLearner.*;

/** All baselines get the same catalog, time budget, new-item cap, and review cap. */
public final class BaselinePlanner implements PlannerStrategy {
    public enum Kind { SHEET_ORDER, RANDOM, PLAIN_SM2 }
    private final Kind kind;
    private final long seed;
    public BaselinePlanner(Kind kind, long seed) { this.kind = kind; this.seed = seed; }
    public String name() { return kind.name(); }

    public List<StudyItem> generatePlan(SimulationCatalog catalog, SyntheticLearner learner, SimulationConfig config, int day) {
        List<StudyItem> result = new ArrayList<>();
        int remaining = config.dailyBudgetMinutes();
        if (kind != Kind.SHEET_ORDER) {
            var due = new ArrayList<>(catalog.problems().stream()
                    .filter(p -> learner.cards().containsKey(p.getId()) && learner.cards().get(p.getId()).dueDay <= day).toList());
            due.sort(Comparator.comparingInt(p -> learner.cards().get(p.getId()).dueDay));
            int reviews = 0;
            for (var problem : due) {
                // A failed/assisted attempt needs a coding retry for every strategy.
                Card card = learner.cards().get(problem.getId());
                boolean recall = card.followUp == null;
                int full = problem.getEstimatedMinutes();
                int cost = recall ? learner.policy().getRecallMinutes() : card.outcome == AttemptOutcome.COULD_NOT_SOLVE ? full : switch (card.followUp) {
                    case EDGE_CASE_LIST -> Math.max(learner.policy().getEdgeCaseMinimumMinutes(), (int) (full * learner.policy().getEdgeCaseTimeFraction()));
                    default -> full;
                };
                if (reviews < config.maxRevisions() && cost <= remaining) {
                    result.add(new StudyItem(problem, true, recall, cost));
                    remaining -= cost;
                    reviews++;
                }
            }
        }
        var unseen = new ArrayList<>(catalog.problems().stream().filter(p -> !learner.cards().containsKey(p.getId())).toList());
        if (kind == Kind.RANDOM) Collections.shuffle(unseen, new Random(seed ^ day));
        int added = 0;
        for (var problem : unseen) {
            int cost = problem.getEstimatedMinutes();
            if (added < config.maxNewProblems() && cost <= remaining) {
                result.add(new StudyItem(problem, false, false, cost));
                remaining -= cost;
                added++;
            }
        }
        return result;
    }

    public void observe(SyntheticLearner learner, Observation observation) {
        Card card = learner.cards().get(observation.item().problem().getId());
        if (kind == Kind.RANDOM) {
            card.dueDay = observation.day() + 7;
        } else if (kind == Kind.PLAIN_SM2) {
            // Fixed ease factor 2.5; quality 5 / 3 / 1 for independent / assisted / failure.
            if (observation.outcome() == AttemptOutcome.COULD_NOT_SOLVE) {
                card.sm2Successes = 0;
                card.sm2Interval = 1;
            } else {
                card.sm2Successes++;
                card.sm2Interval = card.sm2Successes == 1 ? 1 : card.sm2Successes == 2 ? 6
                        : (int) Math.min(365, Math.round(card.sm2Interval * 2.5));
            }
            card.dueDay = observation.day() + card.sm2Interval;
        }
    }
}
