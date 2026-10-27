package com.preptrack.planner.engine;

import com.preptrack.config.EngineProperties;

import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.attempt.entity.ProblemStatus;
import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.attempt.repository.UserProblemAttemptRepository;
import com.preptrack.attempt.service.FollowUpAssigner;
import com.preptrack.attempt.service.FollowUpAssignment;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.planner.entity.PlanItemType;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.entity.RevisionCard;
import com.preptrack.revision.entity.FollowUpType;
import com.preptrack.revision.repository.RevisionCardRepository;
import com.preptrack.revision.service.RevisionTimeEstimator;
import com.preptrack.sheet.entity.SheetProblem;
import com.preptrack.sheet.entity.UserSheet;
import com.preptrack.sheet.repository.SheetProblemRepository;
import com.preptrack.sheet.repository.UserSheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateCollector {

    private final UserProblemStatusRepository userProblemStatusRepository;
    private final RevisionCardRepository revisionCardRepository;
    private final UserSheetRepository userSheetRepository;
    private final SheetProblemRepository sheetProblemRepository;
    private final ProblemPatternRepository problemPatternRepository;
    private final UserPatternStateRepository userPatternStateRepository;
    private final InterviewRepository interviewRepository;
    private final AcquisitionScorer acquisitionScorer;
    private final MaintenanceScorer maintenanceScorer;
    private final RevisionTimeEstimator revisionTimeEstimator;
    private final FollowUpAssigner followUpAssigner;
    private final EngineProperties policy;
    private final UserProblemAttemptRepository userProblemAttemptRepository;

    public List<PlanCandidate> collectCandidates(Long userId, double targetLevel, LocalDate planDate) {
        List<PlanCandidate> candidates = new ArrayList<>();
        Set<Long> seenProblemIds = new HashSet<>();

        // 0. Batch pre-fetch user state and upcoming interviews in 2 queries total
        List<Interview> upcomingInterviews = interviewRepository.findByUserIdAndOutcome(userId, InterviewOutcome.UPCOMING);
        Map<Long, UserPatternState> userStateMap = userPatternStateRepository.findByUserIdWithPattern(userId)
                .stream().collect(Collectors.toMap(s -> s.getPattern().getId(), s -> s));

        // 1. Retries: problems with ATTEMPTED status
        List<UserProblemStatus> attemptedStatuses = userProblemStatusRepository
                .findByUserIdAndStatus(userId, ProblemStatus.ATTEMPTED);

        // 2. Due Revisions
        List<RevisionCard> dueCards = revisionCardRepository.findDueCardsWithProblem(userId, planDate);
        Map<Long, RevisionCard> dueCardByProblem = dueCards.stream()
                .collect(Collectors.toMap(card -> card.getProblem().getId(), card -> card));

        // 3. New Problems from selected sheets (batched in 1 query)
        List<UserSheet> userSheets = userSheetRepository.findByUserIdWithSheet(userId);
        List<Long> sheetIds = userSheets.stream().map(us -> us.getSheet().getId()).toList();
        List<SheetProblem> sheetProblems = sheetIds.isEmpty() ? Collections.emptyList()
                : sheetProblemRepository.findBySheetIdInOrderByDisplayOrderAsc(sheetIds);

        List<UserProblemStatus> solvedStatuses = userProblemStatusRepository.findByUserIdAndStatusIn(
                userId, List.of(ProblemStatus.SOLVED_INDEPENDENTLY, ProblemStatus.SOLVED_WITH_HELP)
        );
        Set<Long> solvedProblemIds = new HashSet<>();
        Set<Long> independentlySolvedProblemIds = new HashSet<>();
        for (UserProblemStatus s : solvedStatuses) {
            solvedProblemIds.add(s.getProblem().getId());
            if (s.getStatus() == ProblemStatus.SOLVED_INDEPENDENTLY) {
                independentlySolvedProblemIds.add(s.getProblem().getId());
            }
        }
        Map<Long, UserProblemStatus> statusByProblem = new HashMap<>();
        attemptedStatuses.forEach(s -> statusByProblem.put(s.getProblem().getId(), s));
        solvedStatuses.forEach(s -> statusByProblem.put(s.getProblem().getId(), s));

        // Collect all distinct problem IDs across retries, revisions, and sheet problems
        Set<Long> allProblemIds = new HashSet<>();
        attemptedStatuses.forEach(ups -> allProblemIds.add(ups.getProblem().getId()));
        dueCards.forEach(card -> allProblemIds.add(card.getProblem().getId()));
        sheetProblems.forEach(sp -> allProblemIds.add(sp.getProblem().getId()));

        Map<Long, List<ProblemPattern>> patternsByProblemId = new HashMap<>();
        if (!allProblemIds.isEmpty()) {
            List<ProblemPattern> pps = problemPatternRepository.findByProblemIdInWithPattern(new ArrayList<>(allProblemIds));
            for (ProblemPattern pp : pps) {
                patternsByProblemId.computeIfAbsent(pp.getProblem().getId(), k -> new ArrayList<>()).add(pp);
            }
        }

        // Compute true per-pattern counts across distinct subscribed sheet problems
        Map<Long, Integer> patternTotalInSheets = new HashMap<>();
        Map<Long, Integer> patternSolvedInSheets = new HashMap<>();
        Set<Long> distinctSheetProblemIds = new HashSet<>();
        for (SheetProblem sp : sheetProblems) {
            Long pid = sp.getProblem().getId();
            if (distinctSheetProblemIds.add(pid)) {
                List<ProblemPattern> pps = patternsByProblemId.get(pid);
                if (pps != null) {
                    for (ProblemPattern pp : pps) {
                        Long patternId = pp.getPattern().getId();
                        patternTotalInSheets.merge(patternId, 1, Integer::sum);
                        if (independentlySolvedProblemIds.contains(pid)) {
                            patternSolvedInSheets.merge(patternId, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        // Batch pre-fetch prerequisites for retry and revision problems
        List<Long> retryAndRevisionProblemIds = new ArrayList<>();
        attemptedStatuses.forEach(ups -> retryAndRevisionProblemIds.add(ups.getProblem().getId()));
        dueCards.forEach(card -> retryAndRevisionProblemIds.add(card.getProblem().getId()));
        Map<Long, Problem> prereqMap = followUpAssigner.getPrereqMap(retryAndRevisionProblemIds);
        List<Long> prerequisiteIds = prereqMap.values().stream().map(Problem::getId).distinct().toList();
        Map<Long, LocalDateTime> independentCodingAt = prerequisiteIds.isEmpty() ? Collections.emptyMap()
                : userProblemAttemptRepository.findLatestIndependentCodingByProblemIds(userId, prerequisiteIds).stream()
                .collect(Collectors.toMap(UserProblemAttemptRepository.IndependentCodingPractice::getProblemId,
                        UserProblemAttemptRepository.IndependentCodingPractice::getLastCodingAt));
        List<Long> missingPrereqPatterns = prereqMap.values().stream().map(Problem::getId).distinct()
                .filter(id -> !patternsByProblemId.containsKey(id)).toList();
        if (!missingPrereqPatterns.isEmpty()) {
            for (ProblemPattern pp : problemPatternRepository.findByProblemIdInWithPattern(missingPrereqPatterns)) {
                patternsByProblemId.computeIfAbsent(pp.getProblem().getId(), ignored -> new ArrayList<>()).add(pp);
            }
        }

        // Process 1: Retries
        for (UserProblemStatus ups : attemptedStatuses) {
            Problem original = ups.getProblem();
            if (!seenProblemIds.add(original.getId())) continue;
            FollowUpAssignment followUp = followUpAssigner.assign(original, ups.getLastFailureReason(), prereqMap);
            RevisionCard dueCard = dueCardByProblem.get(original.getId());
            if (followUp == null && dueCard != null && dueCard.getRequiredFollowUp() != null) {
                followUp = FollowUpAssignment.builder().followUpType(dueCard.getRequiredFollowUp())
                        .followUpDetail(followUpAssigner.getDetailForType(original, dueCard.getRequiredFollowUp(), prereqMap)).build();
            }
            Recovery recovery = recovery(original, followUp != null ? followUp.getFollowUpType() : null,
                    followUp != null ? followUp.getFollowUpDetail() : null,
                    ups.getLastAttemptedAt(), prereqMap, statusByProblem, independentCodingAt);
            Problem p = recovery.problem();
            if (!p.getId().equals(original.getId()) && !seenProblemIds.add(p.getId())) continue;

            List<ProblemPattern> pps = patternsByProblemId.getOrDefault(p.getId(), Collections.emptyList());
            MaintenanceScorer.ScoredResult scored = maintenanceScorer.score(
                    userId, p, targetLevel, planDate, true,
                    recovery.type(),
                    pps, userStateMap, upcomingInterviews, null,
                    patternTotalInSheets, patternSolvedInSheets
            );

            int time = p.getEstimatedMinutes() != null ? p.getEstimatedMinutes() : policy.getDefaultProblemMinutes();

            candidates.add(PlanCandidate.builder()
                    .problem(p)
                    .type(PlanItemType.RETRY)
                    .timeMinutes(time)
                    .value(scored.value)
                    .valueBreakdown(scored.breakdown)
                    .followUpType(recovery.type())
                    .followUpDetail(recovery.detail())
                    .primaryPatternId(scored.primaryPatternId)
                    .interviewDays(scored.interviewDays)
                    .interviewCompany(scored.interviewCompany)
                    .isOverdue(false)
                    .build());
        }

        // Process 2: Due Revisions
        for (RevisionCard card : dueCards) {
            Problem original = card.getProblem();
            if (!seenProblemIds.add(original.getId())) {
                continue;
            }
            UserProblemStatus originalState = statusByProblem.get(original.getId());
            LocalDateTime failedAt = originalState != null ? originalState.getLastAttemptedAt() : card.getLastReviewedAt();
            Recovery recovery = recovery(original, card.getRequiredFollowUp(),
                    card.getRequiredFollowUp() != null ? followUpAssigner.getDetailForType(original, card.getRequiredFollowUp(), prereqMap) : null,
                    failedAt, prereqMap, statusByProblem, independentCodingAt);
            Problem p = recovery.problem();
            boolean warmup = !p.getId().equals(original.getId());
            if (warmup && !seenProblemIds.add(p.getId())) continue;

            List<ProblemPattern> pps = patternsByProblemId.getOrDefault(p.getId(), Collections.emptyList());
            MaintenanceScorer.ScoredResult scored = maintenanceScorer.score(
                    userId, p, targetLevel, planDate, warmup, recovery.type(),
                    pps, userStateMap, upcomingInterviews, card.getNextReviewDate(),
                    patternTotalInSheets, patternSolvedInSheets
            );

            int time = warmup ? (p.getEstimatedMinutes() != null ? p.getEstimatedMinutes() : policy.getDefaultProblemMinutes())
                    : revisionTimeEstimator.estimateMinutes(card);
            boolean isOverdue = card.getNextReviewDate() != null && card.getNextReviewDate().isBefore(planDate);

            candidates.add(PlanCandidate.builder()
                    .problem(p)
                    .type(warmup ? PlanItemType.RETRY : PlanItemType.REVISION)
                    .timeMinutes(time)
                    .value(scored.value)
                    .valueBreakdown(scored.breakdown)
                    .followUpType(recovery.type())
                    .followUpDetail(recovery.detail())
                    .primaryPatternId(scored.primaryPatternId)
                    .interviewDays(scored.interviewDays)
                    .interviewCompany(scored.interviewCompany)
                    .isOverdue(isOverdue)
                    .build());
        }

        // Process 3: New Problems - group by primary pattern, admit top 2 per pattern
        List<Problem> rawNewProblems = new ArrayList<>();
        Set<Long> sheetSeenIds = new HashSet<>();
        for (SheetProblem sp : sheetProblems) {
            Problem p = sp.getProblem();
            if (!solvedProblemIds.contains(p.getId()) && !seenProblemIds.contains(p.getId()) && sheetSeenIds.add(p.getId())) {
                rawNewProblems.add(p);
            }
        }

        Map<Long, List<Problem>> newProblemsByPattern = new LinkedHashMap<>();
        for (Problem p : rawNewProblems) {
            List<ProblemPattern> pps = patternsByProblemId.getOrDefault(p.getId(), Collections.emptyList());
            Long primaryPatternId = null;
            double maxWeight = -1.0;
            for (ProblemPattern pp : pps) {
                double weight = pp.getWeight() != null ? pp.getWeight() : 1.0;
                if (weight > maxWeight) {
                    maxWeight = weight;
                    primaryPatternId = pp.getPattern().getId();
                }
            }
            newProblemsByPattern.computeIfAbsent(primaryPatternId != null ? primaryPatternId : -1L, k -> new ArrayList<>()).add(p);
        }

        Set<Long> scheduledNewPrimaryPatterns = new HashSet<>();
        for (Map.Entry<Long, List<Problem>> entry : newProblemsByPattern.entrySet()) {
            List<Problem> problemsForPattern = entry.getValue();
            int toTake = Math.min(2, problemsForPattern.size());

            for (int i = 0; i < toTake; i++) {
                Problem p = problemsForPattern.get(i);
                seenProblemIds.add(p.getId());

                List<ProblemPattern> pps = patternsByProblemId.getOrDefault(p.getId(), Collections.emptyList());
                AcquisitionScorer.ScoredResult scored = acquisitionScorer.score(
                        userId, p, targetLevel, planDate, scheduledNewPrimaryPatterns,
                        pps, userStateMap, upcomingInterviews,
                        patternTotalInSheets, patternSolvedInSheets
                );

                if (scored.primaryPatternId != null) {
                    scheduledNewPrimaryPatterns.add(scored.primaryPatternId);
                }

                int time = p.getEstimatedMinutes() != null ? p.getEstimatedMinutes() : policy.getDefaultProblemMinutes();

                candidates.add(PlanCandidate.builder()
                        .problem(p)
                        .type(PlanItemType.NEW_PROBLEM)
                        .timeMinutes(time)
                        .value(scored.value)
                        .valueBreakdown(scored.breakdown)
                        .primaryPatternId(scored.primaryPatternId)
                        .interviewDays(scored.interviewDays)
                        .interviewCompany(scored.interviewCompany)
                        .isOverdue(false)
                        .build());
            }
        }

        return candidates;
    }

    private record Recovery(Problem problem, FollowUpType type, String detail) {}

    private Recovery recovery(Problem original, FollowUpType type, String detail, LocalDateTime failedAt,
                              Map<Long, Problem> prerequisites, Map<Long, UserProblemStatus> states,
                              Map<Long, LocalDateTime> independentCodingAt) {
        if (type != FollowUpType.EASIER_PREREQUISITE) return new Recovery(original, type, detail);
        Problem warmup = prerequisites.get(original.getId());
        if (warmup == null || warmup.getId().equals(original.getId())) {
            return new Recovery(original, FollowUpType.CODE_FROM_SCRATCH,
                    "No linked prerequisite is available. Review the pattern, then code this problem from scratch.");
        }
        UserProblemStatus state = states.get(warmup.getId());
        LocalDateTime codingAt = independentCodingAt.get(warmup.getId());
        boolean completedSinceFailure = state != null && state.getLastOutcome() == AttemptOutcome.SOLVED_INDEPENDENTLY
                && codingAt != null && (failedAt == null || !codingAt.isBefore(failedAt));
        if (completedSinceFailure) {
            return new Recovery(original, FollowUpType.CODE_FROM_SCRATCH,
                    "Prerequisite completed: " + warmup.getCanonicalTitle() + ". Retry this problem from scratch.");
        }
        return new Recovery(warmup, FollowUpType.EASIER_PREREQUISITE,
                "Solve this prerequisite independently before retrying " + original.getCanonicalTitle() + ".");
    }
}
