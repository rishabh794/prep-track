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
import com.preptrack.config.EngineProperties;
import com.preptrack.common.service.TimeService;
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

    private final ProblemPatternRepository problemPatternRepository;
    private final UserPatternStateRepository userPatternStateRepository;
    private final RetentionCalculator retentionCalculator;
    private final InterviewRelevanceCalculator interviewRelevanceCalculator;
    private final EngineProperties policy;
    private final TimeService timeService;

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
        return score(userId, problem, targetLevel, planDate, isRetry, followUpType, problemPatterns, stateMap, null);
    }

    public ScoredResult score(
            Long userId,
            Problem problem,
            double targetLevel,
            LocalDate planDate,
            boolean isRetry,
            FollowUpType followUpType,
            List<ProblemPattern> problemPatterns,
            Map<Long, UserPatternState> stateMap,
            List<com.preptrack.application.entity.Interview> upcomingInterviews
    ) {
        return score(userId, problem, targetLevel, planDate, isRetry, followUpType, problemPatterns, stateMap, upcomingInterviews, null, null, null);
    }

    public ScoredResult score(
            Long userId,
            Problem problem,
            double targetLevel,
            LocalDate planDate,
            boolean isRetry,
            FollowUpType followUpType,
            List<ProblemPattern> problemPatterns,
            Map<Long, UserPatternState> stateMap,
            List<com.preptrack.application.entity.Interview> upcomingInterviews,
            LocalDate nextReviewDate,
            Map<Long, Integer> patternTotalInSheets,
            Map<Long, Integer> patternSolvedInSheets
    ) {
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

            UserPatternState state = stateMap != null ? stateMap.get(pattern.getId()) : null;
            double skill = (state != null && state.getSkill() != null) ? state.getSkill() : 0.0;
            double stability = (state != null && state.getStability() != null) ? state.getStability() : policy.getInitialStability();
            int solves = (state != null && state.getIndependentSolves() != null) ? state.getIndependentSolves() : 0;

            var lastPracticed = state != null ? state.getLastPracticedAt() : null;
            if (lastPracticed != null && state.getUser() != null) {
                lastPracticed = timeService.getPracticeDate(lastPracticed, userId).atStartOfDay();
            }
            double retention = retentionCalculator.compute(
                    lastPracticed,
                    stability,
                    (planDate != null ? planDate : timeService.getLocalDate(userId)).atStartOfDay()
            );

            interviewCtx = upcomingInterviews != null
                    ? interviewRelevanceCalculator.calculateWithInterviews(upcomingInterviews, pattern, planDate)
                    : interviewRelevanceCalculator.calculate(userId, pattern, planDate);

            double relevance = interviewCtx.getRelevanceMultiplier();
            double effectiveBoost = interviewRelevanceCalculator.getEffectiveInterviewBoost(state, planDate);
            double totalRelevance = relevance + effectiveBoost;

            // Maintenance floor: max(0.15, target - skill)
            double maintenanceNeed = Math.max(policy.getMaintenanceFloor(), targetLevel - skill) * totalRelevance;
            double forgettingFactor = Math.max(0.0, 1.0 - retention);

            double contrib = weight * maintenanceNeed * forgettingFactor;
            sumValue += contrib;

            int totalInSheets = patternTotalInSheets != null
                    ? patternTotalInSheets.getOrDefault(pattern.getId(), problemPatterns.size())
                    : problemPatterns.size();
            int solvedInSheets = patternSolvedInSheets != null
                    ? patternSolvedInSheets.getOrDefault(pattern.getId(), solves)
                    : solves;

            contributions.add(PatternContribution.builder()
                    .patternId(pattern.getId())
                    .patternName(pattern.getName())
                    .need(Math.round(maintenanceNeed * 100.0) / 100.0)
                    .retention(Math.round(retention * 100.0) / 100.0)
                    .skill(Math.round(skill * 100.0) / 100.0)
                    .linkWeight(weight)
                    .contribution(Math.round(contrib * 100.0) / 100.0)
                    .independentSolves(solvedInSheets)
                    .totalInSheets(totalInSheets)
                    .build());
        }

        double retryMultiplier = isRetry ? policy.getRetryBoostMultiplier() : 1.0;
        sumValue *= retryMultiplier;

        double followUpMultiplier = 1.0;
        if (followUpType != null) {
            followUpMultiplier = switch (followUpType) {
                case EASIER_PREREQUISITE -> policy.getPrerequisiteMultiplier();
                case CODE_FROM_SCRATCH -> policy.getCodeFromScratchMultiplier();
                case EDGE_CASE_LIST -> policy.getEdgeCaseMultiplier();
                case TIMED_RETRY -> policy.getTimedRetryMultiplier();
            };
            sumValue *= followUpMultiplier;
        }

        // Overdue age bonus for due revisions
        double overdueBonus = 1.0;
        if (!isRetry && nextReviewDate != null && planDate != null && nextReviewDate.isBefore(planDate)) {
            long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(nextReviewDate, planDate);
            overdueBonus = 1.0 + Math.min(policy.getMaxOverdueBonus(), overdueDays * policy.getOverdueBonusPerDay());
            sumValue *= overdueBonus;
        }

        double finalTotal = Math.round(sumValue * 100.0) / 100.0;

        ValueBreakdown breakdown = ValueBreakdown.builder()
                .patterns(contributions)
                .interviewRelevance(interviewCtx != null ? interviewCtx.getRelevanceMultiplier() : 1.0)
                .retryBoost(retryMultiplier)
                .followUpMultiplier(followUpMultiplier)
                .diversityPenalty(1.0)
                .overdueBonus(overdueBonus)
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
