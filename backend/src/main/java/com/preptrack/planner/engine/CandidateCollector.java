package com.preptrack.planner.engine;

import com.preptrack.application.entity.Interview;
import com.preptrack.application.entity.InterviewOutcome;
import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.attempt.entity.ProblemStatus;
import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.attempt.service.FollowUpAssigner;
import com.preptrack.attempt.service.FollowUpAssignment;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.planner.entity.PlanItemType;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.entity.RevisionCard;
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

        // 3. New Problems from selected sheets
        List<UserSheet> userSheets = userSheetRepository.findByUserIdWithSheet(userId);
        List<UserProblemStatus> solvedStatuses = userProblemStatusRepository.findByUserIdAndStatusIn(
                userId, List.of(ProblemStatus.SOLVED_INDEPENDENTLY, ProblemStatus.SOLVED_WITH_HELP)
        );
        Set<Long> solvedProblemIds = new HashSet<>();
        for (UserProblemStatus s : solvedStatuses) {
            solvedProblemIds.add(s.getProblem().getId());
        }

        List<Problem> rawNewProblems = new ArrayList<>();
        for (UserSheet us : userSheets) {
            List<SheetProblem> sheetProblems = sheetProblemRepository
                    .findBySheetIdOrderByDisplayOrderAsc(us.getSheet().getId());
            for (SheetProblem sp : sheetProblems) {
                Problem p = sp.getProblem();
                if (!solvedProblemIds.contains(p.getId())) {
                    rawNewProblems.add(p);
                }
            }
        }

        // Collect all distinct problem IDs across retries, revisions, and sheet problems
        Set<Long> allProblemIds = new HashSet<>();
        attemptedStatuses.forEach(ups -> allProblemIds.add(ups.getProblem().getId()));
        dueCards.forEach(card -> allProblemIds.add(card.getProblem().getId()));
        rawNewProblems.forEach(p -> allProblemIds.add(p.getId()));

        Map<Long, List<ProblemPattern>> patternsByProblemId = new HashMap<>();
        if (!allProblemIds.isEmpty()) {
            List<ProblemPattern> pps = problemPatternRepository.findByProblemIdInWithPattern(new ArrayList<>(allProblemIds));
            for (ProblemPattern pp : pps) {
                patternsByProblemId.computeIfAbsent(pp.getProblem().getId(), k -> new ArrayList<>()).add(pp);
            }
        }


        // Process 1: Retries
        for (UserProblemStatus ups : attemptedStatuses) {
            Problem p = ups.getProblem();
            seenProblemIds.add(p.getId());

            List<ProblemPattern> pps = patternsByProblemId.getOrDefault(p.getId(), Collections.emptyList());
            FollowUpAssignment followUp = followUpAssigner.assign(p, ups.getLastFailureReason());
            MaintenanceScorer.ScoredResult scored = maintenanceScorer.score(
                    userId, p, targetLevel, planDate, true,
                    followUp != null ? followUp.getFollowUpType() : null,
                    pps, userStateMap, upcomingInterviews
            );

            int time = p.getEstimatedMinutes() != null ? p.getEstimatedMinutes() : 30;

            candidates.add(PlanCandidate.builder()
                    .problem(p)
                    .type(PlanItemType.RETRY)
                    .timeMinutes(time)
                    .value(scored.value)
                    .valueBreakdown(scored.breakdown)
                    .followUpType(followUp != null ? followUp.getFollowUpType() : null)
                    .followUpDetail(followUp != null ? followUp.getFollowUpDetail() : null)
                    .primaryPatternId(scored.primaryPatternId)
                    .interviewDays(scored.interviewDays)
                    .interviewCompany(scored.interviewCompany)
                    .build());
        }

        // Process 2: Due Revisions
        for (RevisionCard card : dueCards) {
            Problem p = card.getProblem();
            if (seenProblemIds.contains(p.getId())) {
                continue;
            }
            seenProblemIds.add(p.getId());

            List<ProblemPattern> pps = patternsByProblemId.getOrDefault(p.getId(), Collections.emptyList());
            MaintenanceScorer.ScoredResult scored = maintenanceScorer.score(
                    userId, p, targetLevel, planDate, false, card.getRequiredFollowUp(),
                    pps, userStateMap, upcomingInterviews
            );

            int time = revisionTimeEstimator.estimateMinutes(card);

            candidates.add(PlanCandidate.builder()
                    .problem(p)
                    .type(PlanItemType.REVISION)
                    .timeMinutes(time)
                    .value(scored.value)
                    .valueBreakdown(scored.breakdown)
                    .followUpType(card.getRequiredFollowUp())
                    .primaryPatternId(scored.primaryPatternId)
                    .interviewDays(scored.interviewDays)
                    .interviewCompany(scored.interviewCompany)
                    .build());
        }

        // Process 3: New Problems
        Set<Long> scheduledNewPrimaryPatterns = new HashSet<>();
        for (Problem p : rawNewProblems) {
            if (seenProblemIds.contains(p.getId())) {
                continue;
            }
            seenProblemIds.add(p.getId());

            List<ProblemPattern> pps = patternsByProblemId.getOrDefault(p.getId(), Collections.emptyList());
            AcquisitionScorer.ScoredResult scored = acquisitionScorer.score(
                    userId, p, targetLevel, planDate, scheduledNewPrimaryPatterns,
                    pps, userStateMap, upcomingInterviews
            );

            int time = p.getEstimatedMinutes() != null ? p.getEstimatedMinutes() : 30;

            candidates.add(PlanCandidate.builder()
                    .problem(p)
                    .type(PlanItemType.NEW_PROBLEM)
                    .timeMinutes(time)
                    .value(scored.value)
                    .valueBreakdown(scored.breakdown)
                    .primaryPatternId(scored.primaryPatternId)
                    .interviewDays(scored.interviewDays)
                    .interviewCompany(scored.interviewCompany)
                    .build());
        }

        return candidates;
    }
}
