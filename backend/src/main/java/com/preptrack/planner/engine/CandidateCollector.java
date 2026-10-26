package com.preptrack.planner.engine;

import com.preptrack.attempt.entity.ProblemStatus;
import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.attempt.service.FollowUpAssigner;
import com.preptrack.attempt.service.FollowUpAssignment;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateCollector {

    private final UserProblemStatusRepository userProblemStatusRepository;
    private final RevisionCardRepository revisionCardRepository;
    private final UserSheetRepository userSheetRepository;
    private final SheetProblemRepository sheetProblemRepository;
    private final AcquisitionScorer acquisitionScorer;
    private final MaintenanceScorer maintenanceScorer;
    private final RevisionTimeEstimator revisionTimeEstimator;
    private final FollowUpAssigner followUpAssigner;

    public List<PlanCandidate> collectCandidates(Long userId, double targetLevel, LocalDate planDate) {
        List<PlanCandidate> candidates = new ArrayList<>();
        Set<Long> seenProblemIds = new HashSet<>();

        // 1. Retries: problems with ATTEMPTED status
        List<UserProblemStatus> attemptedStatuses = userProblemStatusRepository
                .findByUserIdAndStatus(userId, ProblemStatus.ATTEMPTED);

        for (UserProblemStatus ups : attemptedStatuses) {
            Problem p = ups.getProblem();
            seenProblemIds.add(p.getId());

            FollowUpAssignment followUp = followUpAssigner.assign(p, ups.getLastFailureReason());
            MaintenanceScorer.ScoredResult scored = maintenanceScorer.score(
                    userId, p, targetLevel, planDate, true,
                    followUp != null ? followUp.getFollowUpType() : null
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

        // 2. Due Revisions
        List<RevisionCard> dueCards = revisionCardRepository.findDueCardsWithProblem(userId, planDate);
        for (RevisionCard card : dueCards) {
            Problem p = card.getProblem();
            if (seenProblemIds.contains(p.getId())) {
                continue; // Do not duplicate if already in Retries
            }
            seenProblemIds.add(p.getId());

            MaintenanceScorer.ScoredResult scored = maintenanceScorer.score(
                    userId, p, targetLevel, planDate, false, card.getRequiredFollowUp()
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

        // 3. New Problems from selected sheets
        List<UserSheet> userSheets = userSheetRepository.findByUserIdWithSheet(userId);
        List<UserProblemStatus> solvedStatuses = userProblemStatusRepository.findByUserIdAndStatusIn(
                userId, List.of(ProblemStatus.SOLVED_INDEPENDENTLY, ProblemStatus.SOLVED_WITH_HELP)
        );
        Set<Long> solvedProblemIds = new HashSet<>();
        for (UserProblemStatus s : solvedStatuses) {
            solvedProblemIds.add(s.getProblem().getId());
        }

        Set<Long> scheduledNewPrimaryPatterns = new HashSet<>();

        for (UserSheet us : userSheets) {
            List<SheetProblem> sheetProblems = sheetProblemRepository
                    .findBySheetIdOrderByDisplayOrderAsc(us.getSheet().getId());

            for (SheetProblem sp : sheetProblems) {
                Problem p = sp.getProblem();
                if (seenProblemIds.contains(p.getId()) || solvedProblemIds.contains(p.getId())) {
                    continue; // Skip solved or already considered
                }
                seenProblemIds.add(p.getId());

                AcquisitionScorer.ScoredResult scored = acquisitionScorer.score(
                        userId, p, targetLevel, planDate, scheduledNewPrimaryPatterns
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
        }

        return candidates;
    }
}
