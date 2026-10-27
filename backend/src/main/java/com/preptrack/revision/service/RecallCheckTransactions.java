package com.preptrack.revision.service;

import com.preptrack.ai.dto.RecallCheckResponseDto;
import com.preptrack.ai.dto.RecallGradingRequest;
import com.preptrack.ai.dto.RecallGradingResult;
import com.preptrack.ai.entity.RecallCheck;
import com.preptrack.ai.repository.RecallCheckRepository;
import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.common.service.TimeService;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.problem.repository.ProblemRepository;
import com.preptrack.revision.entity.RevisionCard;
import com.preptrack.revision.repository.RevisionCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/** Short persistence boundaries around grading; no entity escapes as grading input. */
@Service
@RequiredArgsConstructor
public class RecallCheckTransactions {
    private final RevisionCardRepository cards;
    private final ProblemPatternRepository problemPatterns;
    private final UserProblemStatusRepository statuses;
    private final RecallCheckRepository checks;
    private final ProblemRepository problems;
    private final UserRepository users;
    private final TimeService time;

    public record GradingInput(Long cardId, RecallGradingRequest request) {}

    @Transactional(readOnly = true)
    public GradingInput prepareByCard(Long userId, Long cardId, String approach) {
        return snapshot(ownedCard(userId, cardId), userId, approach);
    }

    @Transactional
    public GradingInput prepareByProblem(Long userId, Long problemId, String approach) {
        var user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        var card = cards.findByUserIdAndProblemId(userId, problemId).orElseGet(() -> cards.save(
                RevisionCard.builder().user(user)
                        .problem(problems.findById(problemId).orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + problemId)))
                        .nextReviewDate(time.getLocalDate(userId)).reviewCount(0).build()));
        return snapshot(card, userId, approach);
    }

    private GradingInput snapshot(RevisionCard card, Long userId, String approach) {
        var problem = card.getProblem();
        var patternNames = problemPatterns.findByProblemIdWithPattern(problem.getId()).stream()
                .map(link -> link.getPattern().getName()).collect(Collectors.joining(", "));
        var note = statuses.findByUserIdAndProblemId(userId, problem.getId())
                .map(UserProblemStatus::getInsightNote).orElse(null);
        return new GradingInput(card.getId(), RecallGradingRequest.builder()
                .problemTitle(problem.getCanonicalTitle()).descriptionHint(problem.getDescriptionHint())
                .referenceApproach(problem.getReferenceApproach()).optimalComplexity(problem.getOptimalComplexity())
                .patterns(patternNames).insightNote(note).userApproach(approach).build());
    }

    @Transactional
    public RecallCheckResponseDto persist(Long userId, Long cardId, String approach, RecallGradingResult result) {
        // Revalidate ownership/existence after grading, which may take several seconds.
        var card = ownedCard(userId, cardId);
        var saved = checks.save(RecallCheck.builder().revisionCard(card).userApproach(approach)
                .aiAssessment(result.getGrade()).aiReasoning(result.getReasoning()).finalGrade(result.getGrade())
                .build());
        return RecallCheckResponseDto.builder().recallCheckId(saved.getId()).aiAssessment(saved.getAiAssessment())
                .aiReasoning(saved.getAiReasoning()).canOverride(true).build();
    }

    private RevisionCard ownedCard(Long userId, Long cardId) {
        return cards.findById(cardId).filter(card -> card.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Revision card not found: " + cardId));
    }
}
