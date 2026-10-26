package com.preptrack.revision.service;

import com.preptrack.ai.dto.RecallCheckResponseDto;
import com.preptrack.ai.dto.RecallGradingRequest;
import com.preptrack.ai.dto.RecallGradingResult;
import com.preptrack.ai.entity.RecallCheck;
import com.preptrack.ai.entity.RecallGrade;
import com.preptrack.ai.repository.RecallCheckRepository;
import com.preptrack.ai.service.RecallGradingService;
import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.auth.entity.User;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.service.PatternStateService;
import com.preptrack.planner.entity.DailyPlanItem;
import com.preptrack.planner.repository.DailyPlanItemRepository;
import com.preptrack.problem.entity.Problem;
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

    @Transactional
    public RevisionScheduledDto scheduleRevision(User user, Problem problem, AttemptOutcome outcome, FollowUpType followUpType) {
        LocalDate today = LocalDate.now();
        RevisionCard card = revisionCardRepository.findByUserIdAndProblemId(user.getId(), problem.getId())
                .orElse(null);

        LocalDate nextReviewDate;
        int reviewCount = 0;

        if (card != null) {
            reviewCount = (card.getReviewCount() != null ? card.getReviewCount() : 0) + 1;
            card.setReviewCount(reviewCount);
            card.setLastOutcome(outcome);
            card.setLastReviewedAt(LocalDateTime.now());
            card.setRequiredFollowUp(followUpType);

            if (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY) {
                // Expanding interval based on repetition
                int days = switch (reviewCount) {
                    case 1 -> 3;
                    case 2 -> 7;
                    case 3 -> 14;
                    default -> 30;
                };
                nextReviewDate = today.plusDays(days);
            } else {
                // Missed or needed help -> resurface tomorrow
                nextReviewDate = today.plusDays(1);
            }
            card.setNextReviewDate(nextReviewDate);
            revisionCardRepository.save(card);
        } else {
            // First time attempt - create revision card
            if (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY) {
                nextReviewDate = today.plusDays(3);
            } else {
                nextReviewDate = today.plusDays(1);
            }

            card = RevisionCard.builder()
                    .user(user)
                    .problem(problem)
                    .reviewCount(0)
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

    @Transactional
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

        RecallGradingResult result = recallGradingService.grade(gradingRequest);

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
        scheduleRevision(card.getUser(), card.getProblem(), outcome, null);
        completePlanItemIfPresent(userId, card.getProblem().getId());

        return toDto(card);
    }

    @Transactional
    public RevisionCardDto reviewRevisionWithRecall(Long userId, Long cardId, Map<String, Object> body) {
        RevisionCard card = revisionCardRepository.findById(cardId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Revision card not found: " + cardId));

        AttemptOutcome finalOutcome = AttemptOutcome.SOLVED_INDEPENDENTLY;

        Object recallCheckIdObj = body.get("recallCheckId");
        if (recallCheckIdObj != null) {
            Long checkId = Long.valueOf(recallCheckIdObj.toString());
            RecallCheck check = recallCheckRepository.findById(checkId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recall check not found: " + checkId));

            RecallGrade finalGrade = check.getAiAssessment();
            if (body.get("userOverride") != null) {
                String overrideStr = body.get("userOverride").toString().toUpperCase();
                RecallGrade overrideGrade = parseRecallGrade(overrideStr);
                check.setUserOverride(overrideGrade);
                check.setFinalGrade(overrideGrade);
                finalGrade = overrideGrade;
            } else if (body.get("overrideGrade") != null) {
                String overrideStr = body.get("overrideGrade").toString().toUpperCase();
                RecallGrade overrideGrade = parseRecallGrade(overrideStr);
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
        } else if (body.get("outcome") != null) {
            String outcomeStr = body.get("outcome").toString();
            finalOutcome = parseAttemptOutcome(outcomeStr);
            patternStateService.updatePatternStatesOnAttempt(card.getUser(), card.getProblem(), finalOutcome);
        }

        scheduleRevision(card.getUser(), card.getProblem(), finalOutcome, null);
        completePlanItemIfPresent(userId, card.getProblem().getId());

        return toDto(card);
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
    public RevisionCardDto reviewRevisionByProblemIdWithRecall(Long userId, Long problemId, Map<String, Object> body) {
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
        return reviewRevisionWithRecall(userId, card.getId(), body);
    }
}
