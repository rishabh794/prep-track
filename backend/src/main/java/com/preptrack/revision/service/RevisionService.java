package com.preptrack.revision.service;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.auth.entity.User;
import com.preptrack.common.exception.ResourceNotFoundException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RevisionService {

    private final RevisionCardRepository revisionCardRepository;
    private final RevisionTimeEstimator revisionTimeEstimator;

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
    public RevisionCardDto reviewRevision(Long userId, Long cardId, AttemptOutcome outcome) {
        RevisionCard card = revisionCardRepository.findById(cardId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Revision card not found: " + cardId));

        scheduleRevision(card.getUser(), card.getProblem(), outcome, null);

        return RevisionCardDto.builder()
                .id(card.getId())
                .problemId(card.getProblem().getId())
                .problemTitle(card.getProblem().getCanonicalTitle())
                .difficulty(card.getProblem().getDifficulty() != null ? card.getProblem().getDifficulty().name() : null)
                .estimatedMinutes(revisionTimeEstimator.estimateMinutes(card))
                .nextReviewDate(card.getNextReviewDate())
                .reviewCount(card.getReviewCount())
                .lastOutcome(card.getLastOutcome())
                .lastReviewedAt(card.getLastReviewedAt())
                .requiredFollowUp(card.getRequiredFollowUp())
                .build();
    }
}
