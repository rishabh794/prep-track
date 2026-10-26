package com.preptrack.planner.engine;

import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.planner.dto.PatternContribution;
import com.preptrack.planner.dto.ValueBreakdown;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.entity.FollowUpType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MaintenanceScorer {

    public static final double MAINTENANCE_FLOOR = 0.15;

    private final ProblemPatternRepository problemPatternRepository;
    private final UserPatternStateRepository userPatternStateRepository;
    private final RetentionCalculator retentionCalculator;
    private final InterviewRelevanceCalculator interviewRelevanceCalculator;

    public static class ScoredResult {
        public double value;
        public ValueBreakdown breakdown;
        public Long primaryPatternId;
        public Long interviewDays;
        public String interviewCompany;
    }

    public ScoredResult score(
            Long userId,
            Problem problem,
            double targetLevel,
            LocalDate planDate,
            boolean isRetry,
            FollowUpType followUpType
    ) {
        List<ProblemPattern> problemPatterns = problemPatternRepository.findByProblemIdWithPattern(problem.getId());

        List<Long> patternIds = problemPatterns.stream().map(pp -> pp.getPattern().getId()).toList();
        Map<Long, UserPatternState> stateMap = userPatternStateRepository.findByUserIdAndPatternIdIn(userId, patternIds)
                .stream().collect(Collectors.toMap(s -> s.getPattern().getId(), s -> s));

        List<PatternContribution> contributions = new ArrayList<>();
        double sumValue = 0.0;
        Long primaryPatternId = null;
        double maxWeight = -1.0;

        InterviewRelevanceCalculator.InterviewContext interviewCtx = null;

        for (ProblemPattern pp : problemPatterns) {
            Pattern pattern = pp.getPattern();
            double weight = pp.getWeight() != null ? pp.getWeight() : 1.0;

            if (weight > maxWeight) {
                maxWeight = weight;
                primaryPatternId = pattern.getId();
            }

            UserPatternState state = stateMap.get(pattern.getId());
            double skill = (state != null && state.getSkill() != null) ? state.getSkill() : 0.0;
            double stability = (state != null && state.getStability() != null) ? state.getStability() : 1.0;
            int solves = (state != null && state.getIndependentSolves() != null) ? state.getIndependentSolves() : 0;

            double retention = retentionCalculator.compute(state != null ? state.getLastPracticedAt() : null, stability);

            interviewCtx = interviewRelevanceCalculator.calculate(userId, pattern, planDate);
            double relevance = interviewCtx.getRelevanceMultiplier();

            // Maintenance floor: max(0.15, target - skill)
            double maintenanceNeed = Math.max(MAINTENANCE_FLOOR, targetLevel - skill) * relevance;
            double forgettingFactor = Math.max(0.0, 1.0 - retention);

            double contrib = weight * maintenanceNeed * forgettingFactor;
            sumValue += contrib;

            contributions.add(PatternContribution.builder()
                    .patternId(pattern.getId())
                    .patternName(pattern.getName())
                    .need(Math.round(maintenanceNeed * 100.0) / 100.0)
                    .retention(Math.round(retention * 100.0) / 100.0)
                    .skill(Math.round(skill * 100.0) / 100.0)
                    .linkWeight(weight)
                    .contribution(Math.round(contrib * 100.0) / 100.0)
                    .independentSolves(solves)
                    .totalInSheets(problemPatterns.size())
                    .build());
        }

        double retryMultiplier = isRetry ? 2.0 : 1.0;
        sumValue *= retryMultiplier;

        double followUpMultiplier = 1.0;
        if (followUpType != null) {
            followUpMultiplier = switch (followUpType) {
                case EASIER_PREREQUISITE -> 0.8;
                case CODE_FROM_SCRATCH -> 1.5;
                case EDGE_CASE_LIST -> 1.2;
                case TIMED_RETRY -> 1.3;
            };
            sumValue *= followUpMultiplier;
        }

        double finalTotal = Math.round(sumValue * 100.0) / 100.0;

        ValueBreakdown breakdown = ValueBreakdown.builder()
                .patterns(contributions)
                .interviewRelevance(interviewCtx != null ? interviewCtx.getRelevanceMultiplier() : 1.0)
                .retryBoost(retryMultiplier)
                .followUpMultiplier(followUpMultiplier)
                .diversityPenalty(1.0)
                .totalValue(finalTotal)
                .build();

        ScoredResult res = new ScoredResult();
        res.value = finalTotal;
        res.breakdown = breakdown;
        res.primaryPatternId = primaryPatternId;
        if (interviewCtx != null) {
            res.interviewDays = interviewCtx.getDaysRemaining();
            res.interviewCompany = interviewCtx.getCompanyName();
        }
        return res;
    }
}
