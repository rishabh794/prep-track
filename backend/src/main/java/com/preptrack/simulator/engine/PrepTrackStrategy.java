package com.preptrack.simulator.engine;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.planner.engine.*;
import com.preptrack.planner.entity.PlanItemType;
import com.preptrack.revision.entity.RevisionCard;
import com.preptrack.revision.service.RevisionTimeEstimator;
import com.preptrack.simulator.dto.SimulationConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.preptrack.simulator.engine.SyntheticLearner.*;

@Component
@RequiredArgsConstructor
public class PrepTrackStrategy implements PlannerStrategy {
    private final AcquisitionScorer acquisitionScorer;
    private final MaintenanceScorer maintenanceScorer;
    private final KnapsackSolver solver;
    private final RevisionTimeEstimator timeEstimator;
    public String name() { return "PREPTRACK"; }

    public List<StudyItem> generatePlan(SimulationCatalog catalog, SyntheticLearner learner, SimulationConfig config, int day) {
        List<PlanCandidate> candidates = new ArrayList<>();
        var date = EPOCH.plusDays(day);
        for (var problem : catalog.problems()) {
            Card card = learner.cards().get(problem.getId());
            var links = catalog.links().get(problem.getId());
            if (card == null) {
                var scored = acquisitionScorer.score(0L, problem, config.targetSkill(), date, Set.of(),
                        links, learner.estimates(), List.of());
                candidates.add(PlanCandidate.builder().problem(problem).type(PlanItemType.NEW_PROBLEM)
                        .timeMinutes(problem.getEstimatedMinutes()).value(scored.value).primaryPatternId(scored.primaryPatternId).build());
            } else if (card.dueDay <= day) {
                boolean retry = card.outcome == AttemptOutcome.COULD_NOT_SOLVE;
                var scored = maintenanceScorer.score(0L, problem, config.targetSkill(), date, retry, card.followUp,
                        links, learner.estimates(), List.of(), EPOCH.plusDays(card.dueDay), null, null);
                int cost = retry ? problem.getEstimatedMinutes() : timeEstimator.estimateMinutes(
                        RevisionCard.builder().problem(problem).requiredFollowUp(card.followUp).build());
                candidates.add(PlanCandidate.builder().problem(problem).type(retry ? PlanItemType.RETRY : PlanItemType.REVISION)
                        .timeMinutes(cost).value(scored.value).followUpType(card.followUp).build());
            }
        }
        return solver.solve(candidates, config.dailyBudgetMinutes(), config.maxRevisions(), config.maxNewProblems()).stream()
                .map(c -> new StudyItem(c.getProblem(), c.getType() != PlanItemType.NEW_PROBLEM,
                        c.getType() == PlanItemType.REVISION && c.getFollowUpType() == null, c.getTimeMinutes())).toList();
    }
}
