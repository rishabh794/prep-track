package com.preptrack.revision.service;

import com.preptrack.ai.dto.RecallCheckResponseDto;
import com.preptrack.ai.dto.RecallGradingRequest;
import com.preptrack.ai.dto.RecallGradingResult;
import com.preptrack.ai.entity.RecallCheck;
import com.preptrack.ai.entity.RecallGrade;
import com.preptrack.ai.repository.RecallCheckRepository;
import com.preptrack.ai.service.RecallGradingService;
import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.attempt.entity.ProblemStatus;
import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.auth.entity.User;
import com.preptrack.config.EngineProperties;
import com.preptrack.common.exception.BadRequestException;
import com.preptrack.common.exception.ResourceNotFoundException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Transactional
    public RevisionScheduledDto scheduleRevision(User user, Problem problem, AttemptOutcome outcome, FollowUpType followUpType) {
        LocalDate today = LocalDate.now();
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
            card.setLastReviewedAt(LocalDateTime.now());
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
                    .lastReviewedAt(LocalDateTime.now())
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

    public RecallCheckResponseDto submitRecallCheck(Long userId, Long cardId, String userApproach) {
        RevisionCard card = revisionCardRepository.findById(cardId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Revision card not found: " + cardId));

        Problem problem = card.getProblem();
        List<ProblemPattern> problemPatterns = problemPatternRepository.findByProblemIdWithPattern(problem.getId());
        String patternsStr = problemPatterns.stream()
                .map(pp -> pp.getPattern().getName())
                .collect(Collectors.joining(", "));

        Optional<UserProblemStatus> statusOpt = userProblemStatusRepository.findByUserIdAndProblemId(userId, problem.getId());
        String insightNote = statusOpt.map(UserProblemStatus::getInsightNote).orElse(null);

        RecallGradingRequest gradingRequest = RecallGradingRequest.builder()
                .problemTitle(problem.getCanonicalTitle())
                .descriptionHint(problem.getDescriptionHint())
                .referenceApproach(problem.getReferenceApproach())
                .optimalComplexity(problem.getOptimalComplexity())
                .patterns(patternsStr)
                .insightNote(insightNote)
                .userApproach(userApproach)
                .build();

        // Remote AI call executed outside transaction to prevent connection pool exhaustion
        RecallGradingResult result = recallGradingService.grade(gradingRequest);

        return saveRecallCheck(cardId, userApproach, result);
    }

    @Transactional
    public RecallCheckResponseDto saveRecallCheck(Long cardId, String userApproach, RecallGradingResult result) {
        RevisionCard card = revisionCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Revision card not found: " + cardId));

        RecallCheck check = RecallCheck.builder()
                .revisionCard(card)
                .userApproach(userApproach != null ? userApproach : "")
                .aiAssessment(result.getGrade())
                .aiReasoning(result.getReasoning())
                .userOverride(null)
                .finalGrade(result.getGrade())
                .build();

        RecallCheck saved = recallCheckRepository.save(check);

        return RecallCheckResponseDto.builder()
                .recallCheckId(saved.getId())
                .aiAssessment(saved.getAiAssessment())
                .aiReasoning(saved.getAiReasoning())
                .canOverride(true)
                .build();
    }

    @Transactional
    public RevisionCardDto reviewRevision(Long userId, Long cardId, AttemptOutcome outcome) {
        RevisionCard card = revisionCardRepository.findById(cardId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Revision card not found: " + cardId));

        patternStateService.updatePatternStatesOnAttempt(card.getUser(), card.getProblem(), outcome);
        FollowUpType followUpType = switch (outcome) {
            case COULD_NOT_SOLVE -> FollowUpType.CODE_FROM_SCRATCH;
            case SOLVED_WITH_HELP -> FollowUpType.EDGE_CASE_LIST;
            case SOLVED_INDEPENDENTLY -> null;
        };
        scheduleRevision(card.getUser(), card.getProblem(), outcome, followUpType);
        updateUserProblemStatusOnReview(card.getUser(), card.getProblem(), outcome);
        completePlanItemIfPresent(userId, card.getProblem().getId());

        return toDto(card);
    }

    @Transactional
    public RevisionCardDto reviewRevisionWithRecall(Long userId, Long cardId, ReviewRevisionRequest request) {
        RevisionCard card = revisionCardRepository.findById(cardId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Revision card not found: " + cardId));

        if (request == null || (request.getRecallCheckId() == null && request.getOutcome() == null)) {
            throw new BadRequestException("Either recallCheckId or outcome must be provided");
        }

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

            RecallGrade finalGrade = check.getAiAssessment();
            String overrideVal = request.getUserOverride() != null ? request.getUserOverride() : request.getOverrideGrade();
            if (overrideVal != null && !overrideVal.trim().isEmpty()) {
                RecallGrade overrideGrade = parseRecallGrade(overrideVal.trim().toUpperCase());
                check.setUserOverride(overrideGrade);
                check.setFinalGrade(overrideGrade);
                finalGrade = overrideGrade;
            }
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
            patternStateService.updatePatternStatesOnAttempt(card.getUser(), card.getProblem(), finalOutcome);
            followUpType = switch (finalOutcome) {
                case COULD_NOT_SOLVE -> FollowUpType.CODE_FROM_SCRATCH;
                case SOLVED_WITH_HELP -> FollowUpType.EDGE_CASE_LIST;
                case SOLVED_INDEPENDENTLY -> null;
            };
        }

        scheduleRevision(card.getUser(), card.getProblem(), finalOutcome, followUpType);
        updateUserProblemStatusOnReview(card.getUser(), card.getProblem(), finalOutcome);
        completePlanItemIfPresent(userId, card.getProblem().getId());

        return toDto(card);
    }

    private void updateUserProblemStatusOnReview(User user, Problem problem, AttemptOutcome outcome) {
        UserProblemStatus status = userProblemStatusRepository.findByUserIdAndProblemId(user.getId(), problem.getId())
                .orElseGet(() -> UserProblemStatus.builder()
                        .user(user)
                        .problem(problem)
                        .status(ProblemStatus.UNSOLVED)
                        .totalAttempts(0)
                        .independentSolves(0)
                        .build());

        LocalDateTime now = LocalDateTime.now();
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
        status.setLastAttemptedAt(now);
        userProblemStatusRepository.save(status);
    }

    private void completePlanItemIfPresent(Long userId, Long problemId) {
        Optional<DailyPlanItem> planItemOpt = dailyPlanItemRepository.findItemForToday(
                userId, LocalDate.now(), problemId
        );
        if (planItemOpt.isPresent()) {
            DailyPlanItem item = planItemOpt.get();
            item.setCompleted(true);
            item.setCompletedAt(LocalDateTime.now());
            dailyPlanItemRepository.save(item);
        }
    }

    private RecallGrade parseRecallGrade(String str) {
        try {
            return RecallGrade.valueOf(str);
        } catch (Exception e) {
            return switch (str) {
                case "SOLVED_INDEPENDENTLY" -> RecallGrade.PASS;
                case "SOLVED_WITH_HELP" -> RecallGrade.PARTIAL;
                default -> RecallGrade.FAIL;
            };
        }
    }

    private AttemptOutcome parseAttemptOutcome(String str) {
        try {
            return AttemptOutcome.valueOf(str);
        } catch (Exception e) {
            return switch (str.toUpperCase()) {
                case "PASS" -> AttemptOutcome.SOLVED_INDEPENDENTLY;
                case "PARTIAL" -> AttemptOutcome.SOLVED_WITH_HELP;
                default -> AttemptOutcome.COULD_NOT_SOLVE;
            };
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

    @Transactional
    public RecallCheckResponseDto submitRecallCheckByProblemId(Long userId, Long problemId, String userApproach) {
        RevisionCard card = revisionCardRepository.findByUserIdAndProblemId(userId, problemId)
                .orElseGet(() -> {
                    Problem p = problemRepository.findById(problemId)
                            .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + problemId));
                    User u = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
                    return revisionCardRepository.save(RevisionCard.builder()
                            .user(u)
                            .problem(p)
                            .nextReviewDate(LocalDate.now())
                            .reviewCount(0)
                            .build());
                });
        return submitRecallCheck(userId, card.getId(), userApproach);
    }

    @Transactional
    public RevisionCardDto reviewRevisionByProblemIdWithRecall(Long userId, Long problemId, ReviewRevisionRequest request) {
        RevisionCard card = revisionCardRepository.findByUserIdAndProblemId(userId, problemId)
                .orElseGet(() -> {
                    Problem p = problemRepository.findById(problemId)
                            .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + problemId));
                    User u = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
                    return revisionCardRepository.save(RevisionCard.builder()
                            .user(u)
                            .problem(p)
                            .nextReviewDate(LocalDate.now())
                            .reviewCount(0)
                            .build());
                });
        return reviewRevisionWithRecall(userId, card.getId(), request);
    }
}
