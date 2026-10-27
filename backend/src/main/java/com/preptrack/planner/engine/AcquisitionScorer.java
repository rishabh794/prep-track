package com.preptrack.planner.engine;

import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.planner.dto.PatternContribution;
import com.preptrack.planner.dto.ValueBreakdown;
import com.preptrack.problem.entity.Problem;
import com.preptrack.config.EngineProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AcquisitionScorer {

    private final ProblemPatternRepository problemPatternRepository;
    private final UserPatternStateRepository userPatternStateRepository;
    private final InterviewRelevanceCalculator interviewRelevanceCalculator;
    private final EngineProperties policy;

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
            Set<Long> scheduledNewPrimaryPatterns
    ) {
        List<ProblemPattern> problemPatterns = problemPatternRepository.findByProblemIdWithPattern(problem.getId());
        List<Long> patternIds = problemPatterns.stream().map(pp -> pp.getPattern().getId()).toList();
        Map<Long, UserPatternState> stateMap = userPatternStateRepository.findByUserIdAndPatternIdIn(userId, patternIds)
                .stream().collect(Collectors.toMap(s -> s.getPattern().getId(), s -> s));
        return score(userId, problem, targetLevel, planDate, scheduledNewPrimaryPatterns, problemPatterns, stateMap, null);
    }

    public ScoredResult score(
            Long userId,
            Problem problem,
            double targetLevel,
            LocalDate planDate,
            Set<Long> scheduledNewPrimaryPatterns,
            List<ProblemPattern> problemPatterns,
            Map<Long, UserPatternState> stateMap,
            List<com.preptrack.application.entity.Interview> upcomingInterviews
    ) {
        return score(userId, problem, targetLevel, planDate, scheduledNewPrimaryPatterns, problemPatterns, stateMap, upcomingInterviews, null, null);
    }

    public ScoredResult score(
            Long userId,
            Problem problem,
            double targetLevel,
            LocalDate planDate,
            Set<Long> scheduledNewPrimaryPatterns,
            List<ProblemPattern> problemPatterns,
            Map<Long, UserPatternState> stateMap,
            List<com.preptrack.application.entity.Interview> upcomingInterviews,
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
            int solves = (state != null && state.getIndependentSolves() != null) ? state.getIndependentSolves() : 0;

            // Clamped at 0.0 - no negative values!
            double skillGap = Math.max(0.0, targetLevel - skill);

            interviewCtx = upcomingInterviews != null
                    ? interviewRelevanceCalculator.calculateWithInterviews(upcomingInterviews, pattern, planDate)
                    : interviewRelevanceCalculator.calculate(userId, pattern, planDate);

            double relevance = interviewCtx.getRelevanceMultiplier();
            double effectiveBoost = interviewRelevanceCalculator.getEffectiveInterviewBoost(state, planDate);
            double totalRelevance = relevance + effectiveBoost;

            double contrib = weight * skillGap * totalRelevance;
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
                    .need(Math.round(skillGap * 100.0) / 100.0)
                    .retention(0.0) // Acquisition does not care about retention
                    .skill(Math.round(skill * 100.0) / 100.0)
                    .linkWeight(weight)
                    .contribution(Math.round(contrib * 100.0) / 100.0)
                    .independentSolves(solvedInSheets)
                    .totalInSheets(totalInSheets)
                    .build());
        }

        double diversityMultiplier = 1.0;
        if (primaryPatternId != null && scheduledNewPrimaryPatterns != null && scheduledNewPrimaryPatterns.contains(primaryPatternId)) {
            diversityMultiplier = policy.getDiversityMultiplier();
            sumValue *= diversityMultiplier;
        }

        double finalTotal = Math.round(sumValue * 100.0) / 100.0;

        ValueBreakdown breakdown = ValueBreakdown.builder()
                .patterns(contributions)
                .interviewRelevance(interviewCtx != null ? interviewCtx.getRelevanceMultiplier() : 1.0)
                .retryBoost(1.0)
                .followUpMultiplier(1.0)
                .diversityPenalty(diversityMultiplier)
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
