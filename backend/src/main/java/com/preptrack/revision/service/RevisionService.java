package com.preptrack.revision.service;

import com.preptrack.ai.dto.RecallCheckResponseDto;
import com.preptrack.ai.dto.RecallGradingResult;
import com.preptrack.ai.entity.RecallCheck;
import com.preptrack.ai.entity.RecallGrade;
import com.preptrack.ai.repository.RecallCheckRepository;
import com.preptrack.ai.service.RecallGradingService;
import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.attempt.entity.FailureReason;
import com.preptrack.attempt.entity.ProblemStatus;
import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.attempt.service.AttemptHistoryService;
import com.preptrack.attempt.service.FollowUpAssigner;
import com.preptrack.auth.entity.User;
import com.preptrack.config.EngineProperties;
import com.preptrack.common.exception.BadRequestException;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.common.service.TimeService;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.service.PatternStateService;
import com.preptrack.planner.entity.DailyPlanItem;
import com.preptrack.planner.repository.DailyPlanItemRepository;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.dto.ReviewRevisionRequest;
import com.preptrack.revision.dto.RevisionCardDto;
import com.preptrack.revision.dto.RevisionScheduledDto;
import com.preptrack.revision.entity.FollowUpType;
import com.preptrack.revision.entity.RevisionCard;
import com.preptrack.revision.repository.RevisionCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevisionService {

    private final RevisionCardRepository revisionCardRepository;
    private final RevisionTimeEstimator revisionTimeEstimator;
    private final RecallGradingService recallGradingService;
    private final RecallCheckRepository recallCheckRepository;
    private final UserProblemStatusRepository userProblemStatusRepository;
    private final ProblemPatternRepository problemPatternRepository;
    private final PatternStateService patternStateService;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final com.preptrack.problem.repository.ProblemRepository problemRepository;
    private final com.preptrack.auth.repository.UserRepository userRepository;
    private final com.preptrack.pattern.repository.UserPatternStateRepository userPatternStateRepository;
    private final EngineProperties policy;
    private final RevisionSchedulingPolicy schedulingPolicy;
    private final RecallCheckTransactions recallTransactions;
    private final AttemptHistoryService attemptHistoryService;
    private final FollowUpAssigner followUpAssigner;
    private final TimeService timeService;

    @Transactional
    public RevisionScheduledDto scheduleRevision(User user, Problem problem, AttemptOutcome outcome, FollowUpType followUpType) {
        LocalDate today = timeService.getLocalDate(user.getId());
        RevisionCard card = revisionCardRepository.findByUserIdAndProblemId(user.getId(), problem.getId())
                .orElse(null);

        LocalDate nextReviewDate;
        int reviewCount;
        if (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY) {
            reviewCount = (card != null && card.getReviewCount() != null ? card.getReviewCount() : 0) + 1;
        } else if (outcome == AttemptOutcome.SOLVED_WITH_HELP) {
            reviewCount = (card != null && card.getReviewCount() != null && card.getReviewCount() > 0) ? card.getReviewCount() : 1;
        } else {
            reviewCount = 0;
        }

        if (card != null) {
            card.setReviewCount(reviewCount);
            card.setLastOutcome(outcome);
            card.setLastReviewedAt(timeService.now());
            card.setRequiredFollowUp(followUpType);

            if (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY) {
                List<ProblemPattern> pps = problemPatternRepository.findByProblemIdWithPattern(problem.getId());
                double minStability = Double.MAX_VALUE;
                for (ProblemPattern pp : pps) {
                    var stateOpt = userPatternStateRepository.findByUserIdAndPatternId(user.getId(), pp.getPattern().getId());
                    if (stateOpt.isPresent() && stateOpt.get().getStability() != null) {
                        minStability = Math.min(minStability, stateOpt.get().getStability());
                    }
                }
                if (minStability == Double.MAX_VALUE) {
                    minStability = policy.getRevisionFallbackStability();
                }
                int days = schedulingPolicy.intervalDays(outcome, reviewCount, false, minStability);
                nextReviewDate = today.plusDays(days);
            } else {
                nextReviewDate = today.plusDays(policy.getRevisionRetryDays());
            }
            card.setNextReviewDate(nextReviewDate);
            revisionCardRepository.save(card);
        } else {
            // First time attempt - create revision card
            if (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY) {
                nextReviewDate = today.plusDays(policy.revisionIntervalDays(1));
            } else {
                nextReviewDate = today.plusDays(policy.getRevisionRetryDays());
            }

            card = RevisionCard.builder()
                    .user(user)
                    .problem(problem)
                    .reviewCount(reviewCount)
                    .lastOutcome(outcome)
                    .lastReviewedAt(timeService.now())
                    .requiredFollowUp(followUpType)
                    .nextReviewDate(nextReviewDate)
                    .build();
            revisionCardRepository.save(card);
        }

        return RevisionScheduledDto.builder()
                .nextReviewDate(nextReviewDate)
                .followUpType(followUpType)
                .build();
    }

    @Transactional(readOnly = true)
    public List<RevisionCardDto> getDueRevisions(Long userId, LocalDate date) {
        List<RevisionCard> dueCards = revisionCardRepository.findDueCardsWithProblem(userId, date);
        return dueCards.stream().map(c -> RevisionCardDto.builder()
                .id(c.getId())
                .problemId(c.getProblem().getId())
                .problemTitle(c.getProblem().getCanonicalTitle())
                .difficulty(c.getProblem().getDifficulty() != null ? c.getProblem().getDifficulty().name() : null)
                .estimatedMinutes(revisionTimeEstimator.estimateMinutes(c))
                .nextReviewDate(c.getNextReviewDate())
                .reviewCount(c.getReviewCount())
                .lastOutcome(c.getLastOutcome())
                .lastReviewedAt(c.getLastReviewedAt())
                .requiredFollowUp(c.getRequiredFollowUp())
                .build()
        ).toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RecallCheckResponseDto submitRecallCheck(Long userId, Long cardId, String userApproach) {
        var input = recallTransactions.prepareByCard(userId, cardId, userApproach);
        RecallGradingResult result = recallGradingService.grade(input.request());
        return recallTransactions.persist(userId, input.cardId(), userApproach, result);
    }

    @Transactional
    public RevisionCardDto reviewRevision(Long userId, Long cardId, AttemptOutcome outcome) {
        if (outcome == null) throw new BadRequestException("Outcome is required");
        return reviewRevisionWithRecall(userId, cardId, ReviewRevisionRequest.builder().outcome(outcome.name()).build());
    }

    @Transactional
    public RevisionCardDto reviewRevisionWithRecall(Long userId, Long cardId, ReviewRevisionRequest request) {
        // All attempts and confirmations for a learner share this lock. A waiting
        // confirmation reads confirmedAt only after the first update commits.
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        RevisionCard card = revisionCardRepository.findById(cardId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Revision card not found: " + cardId));

        if (request == null || (request.getRecallCheckId() == null && request.getOutcome() == null)) {
            throw new BadRequestException("Either recallCheckId or outcome must be provided");
        }
        validateReviewSignals(request);

        AttemptOutcome finalOutcome;
        FollowUpType followUpType = null;

        if (request.getRecallCheckId() != null) {
            Long checkId = request.getRecallCheckId();
            RecallCheck check = recallCheckRepository.findById(checkId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recall check not found: " + checkId));

            // IDOR check: Verify recall check belongs to this revision card
            if (check.getRevisionCard() == null || !check.getRevisionCard().getId().equals(card.getId())) {
                throw new BadRequestException("Recall check does not belong to this revision card");
            }
            if (Boolean.TRUE.equals(check.getLegacyInvalidated())) {
                throw new BadRequestException("This legacy recall check has expired. Submit a fresh recall before confirming.");
            }
            String overrideVal = request.getUserOverride() != null ? request.getUserOverride() : request.getOverrideGrade();
            RecallGrade overrideGrade = overrideVal != null && !overrideVal.isBlank() ? parseRecallGrade(overrideVal) : null;
            if (check.getConfirmedAt() != null) {
                if (overrideGrade != null && overrideGrade != check.getFinalGrade()) {
                    throw new BadRequestException("Recall check has already been confirmed with a different grade");
                }
                return toDto(card);
            }

            RecallGrade finalGrade = check.getAiAssessment();
            if (overrideVal != null && !overrideVal.trim().isEmpty()) {
                check.setUserOverride(overrideGrade);
                check.setFinalGrade(overrideGrade);
                finalGrade = overrideGrade;
            }
            if (finalGrade == RecallGrade.PASS && request.getHintsUsed() != null && request.getHintsUsed() > 0) {
                throw new BadRequestException("An independent recall cannot use hints; choose a partial grade");
            }
            check.setConfirmedAt(timeService.now());
            recallCheckRepository.save(check);

            patternStateService.updatePatternStatesOnRecallGrade(card.getUser(), card.getProblem(), finalGrade);

            finalOutcome = switch (finalGrade) {
                case PASS -> AttemptOutcome.SOLVED_INDEPENDENTLY;
                case PARTIAL -> AttemptOutcome.SOLVED_WITH_HELP;
                case FAIL -> AttemptOutcome.COULD_NOT_SOLVE;
            };

            followUpType = switch (finalGrade) {
                case FAIL -> FollowUpType.CODE_FROM_SCRATCH;
                case PARTIAL -> FollowUpType.EDGE_CASE_LIST;
                case PASS -> null;
            };
        } else {
            finalOutcome = parseAttemptOutcome(request.getOutcome());
            if (finalOutcome == AttemptOutcome.SOLVED_INDEPENDENTLY && request.getHintsUsed() != null && request.getHintsUsed() > 0) {
                throw new BadRequestException("An independent solve cannot use hints");
            }
            patternStateService.updatePatternStatesOnAttempt(card.getUser(), card.getProblem(), finalOutcome);
            followUpType = switch (finalOutcome) {
                case COULD_NOT_SOLVE -> FollowUpType.CODE_FROM_SCRATCH;
                case SOLVED_WITH_HELP -> FollowUpType.EDGE_CASE_LIST;
                case SOLVED_INDEPENDENTLY -> null;
            };
            if (finalOutcome != AttemptOutcome.SOLVED_INDEPENDENTLY && request.getFailureReason() != null) {
                followUpType = followUpAssigner.assign(card.getProblem(), request.getFailureReason()).getFollowUpType();
            }
        }

        scheduleRevision(card.getUser(), card.getProblem(), finalOutcome, followUpType);
        updateUserProblemStatusOnReview(card.getUser(), card.getProblem(), finalOutcome, request.getFailureReason());
        attemptHistoryService.recordReview(card.getUser(), card.getProblem(), finalOutcome, request.getRecallCheckId() != null,
                request.getRecallCheckId(), request.getFailureReason(), request.getTimeTakenSec(), request.getHintsUsed(), request.getNotes(), followUpType);
        completePlanItemIfPresent(userId, card.getProblem().getId());

        return toDto(card);
    }

    private void updateUserProblemStatusOnReview(User user, Problem problem, AttemptOutcome outcome, FailureReason reason) {
        UserProblemStatus status = userProblemStatusRepository.findByUserIdAndProblemId(user.getId(), problem.getId())
                .orElseGet(() -> UserProblemStatus.builder()
                        .user(user)
                        .problem(problem)
                        .status(ProblemStatus.UNSOLVED)
                        .totalAttempts(0)
                        .independentSolves(0)
                        .build());

        LocalDateTime now = timeService.now();
        ProblemStatus newStatus;
        if (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY) {
            newStatus = ProblemStatus.SOLVED_INDEPENDENTLY;
            if (status.getFirstSolvedAt() == null) {
                status.setFirstSolvedAt(now);
            }
            status.setIndependentSolves((status.getIndependentSolves() != null ? status.getIndependentSolves() : 0) + 1);
        } else if (outcome == AttemptOutcome.SOLVED_WITH_HELP) {
            newStatus = status.getStatus() == ProblemStatus.SOLVED_INDEPENDENTLY ? ProblemStatus.SOLVED_INDEPENDENTLY : ProblemStatus.SOLVED_WITH_HELP;
            if (status.getFirstSolvedAt() == null) {
                status.setFirstSolvedAt(now);
            }
        } else {
            newStatus = (status.getFirstSolvedAt() != null) ? status.getStatus() : ProblemStatus.ATTEMPTED;
        }

        status.setStatus(newStatus);
        status.setTotalAttempts((status.getTotalAttempts() != null ? status.getTotalAttempts() : 0) + 1);
        status.setLastOutcome(outcome);
        status.setLastFailureReason(reason);
        status.setLastAttemptedAt(now);
        userProblemStatusRepository.save(status);
    }

    private void completePlanItemIfPresent(Long userId, Long problemId) {
        Optional<DailyPlanItem> planItemOpt = dailyPlanItemRepository.findItemForToday(
                userId, timeService.getLocalDate(userId), problemId
        );
        if (planItemOpt.isPresent()) {
            DailyPlanItem item = planItemOpt.get();
            item.setCompleted(true);
            item.setSkipped(false);
            item.setSkippedReason(null);
            item.setCompletedAt(timeService.now());
            dailyPlanItemRepository.save(item);
        }
    }

    private RecallGrade parseRecallGrade(String str) {
        String normalized = str == null ? "" : str.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return RecallGrade.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return switch (normalized) {
                case "SOLVED_INDEPENDENTLY" -> RecallGrade.PASS;
                case "SOLVED_WITH_HELP" -> RecallGrade.PARTIAL;
                case "COULD_NOT_SOLVE" -> RecallGrade.FAIL;
                default -> throw new BadRequestException("Invalid recall grade: " + str);
            };
        }
    }

    private AttemptOutcome parseAttemptOutcome(String str) {
        String normalized = str == null ? "" : str.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return AttemptOutcome.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return switch (normalized) {
                case "PASS" -> AttemptOutcome.SOLVED_INDEPENDENTLY;
                case "PARTIAL" -> AttemptOutcome.SOLVED_WITH_HELP;
                case "FAIL" -> AttemptOutcome.COULD_NOT_SOLVE;
                default -> throw new BadRequestException("Invalid revision outcome: " + str);
            };
        }
    }

    private void validateReviewSignals(ReviewRevisionRequest request) {
        if ((request.getTimeTakenSec() != null && request.getTimeTakenSec() < 0)
                || (request.getHintsUsed() != null && request.getHintsUsed() < 0)) {
            throw new BadRequestException("Time taken and hints used cannot be negative");
        }
        if (request.getRecallCheckId() != null && request.getOutcome() != null) {
            throw new BadRequestException("Provide either a recall check or a coding outcome, not both");
        }
    }

    private RevisionCardDto toDto(RevisionCard card) {
        return RevisionCardDto.builder()
                .id(card.getId())
                .problemId(card.getProblem().getId())
                .problemTitle(card.getProblem().getCanonicalTitle())
                .difficulty(card.getProblem().getDifficulty() != null ? card.getProblem().getDifficulty().name() : null)
                .estimatedMinutes(revisionTimeEstimator.estimateMinutes(card))
                .nextReviewDate(card.getNextReviewDate())
                .reviewCount(card.getReviewCount())
                .lastOutcome(card.getLastOutcome())
                .requiredFollowUp(card.getRequiredFollowUp())
                .build();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RecallCheckResponseDto submitRecallCheckByProblemId(Long userId, Long problemId, String userApproach) {
        var input = recallTransactions.prepareByProblem(userId, problemId, userApproach);
        var result = recallGradingService.grade(input.request());
        return recallTransactions.persist(userId, input.cardId(), userApproach, result);
    }

    @Transactional
    public RevisionCardDto reviewRevisionByProblemIdWithRecall(Long userId, Long problemId, ReviewRevisionRequest request) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        RevisionCard card = revisionCardRepository.findByUserIdAndProblemId(userId, problemId)
                .orElseGet(() -> {
                    Problem p = problemRepository.findById(problemId)
                            .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + problemId));
                    User u = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
                    return revisionCardRepository.save(RevisionCard.builder()
                            .user(u)
                            .problem(p)
                            .nextReviewDate(timeService.getLocalDate(userId))
                            .reviewCount(0)
                            .build());
                });
        return reviewRevisionWithRecall(userId, card.getId(), request);
    }
}
