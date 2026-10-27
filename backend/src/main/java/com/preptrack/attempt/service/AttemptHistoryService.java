package com.preptrack.attempt.service;

import com.preptrack.attempt.dto.*;
import com.preptrack.attempt.entity.*;
import com.preptrack.attempt.repository.*;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.*;
import com.preptrack.common.service.TimeService;
import com.preptrack.problem.entity.Problem;
import com.preptrack.problem.repository.ProblemRepository;
import com.preptrack.revision.entity.FollowUpType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttemptHistoryService {
    private final UserProblemAttemptRepository attempts;
    private final UserProblemStatusRepository statuses;
    private final ProblemRepository problems;
    private final UserRepository users;
    private final TimeService timeService;

    @Transactional(readOnly = true)
    public AttemptHistoryPage getHistory(Long userId, Long problemId, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new BadRequestException("Page must have a supported offset and size must be between 1 and 100");
        if (problemId != null && !problems.existsById(problemId)) throw new ResourceNotFoundException("Problem not found: " + problemId);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("attemptedAt"), Sort.Order.desc("id")));
        var result = problemId == null ? attempts.findByUserId(userId, pageable) : attempts.findByUserIdAndProblemId(userId, problemId, pageable);
        var content = result.getContent().stream().map(attempt -> new AttemptHistoryDto(attempt.getId(), attempt.getProblem().getId(),
                attempt.getProblem().getCanonicalTitle(), attempt.getActivityType(), attempt.getOutcome(), attempt.getFailureReason(),
                attempt.getTimeTakenSec(), attempt.getHintsUsed(), attempt.getIsRevision(), attempt.getRecallCheckId(),
                attempt.getFollowUpType(), attempt.getNotes(), timeService.toInstant(attempt.getAttemptedAt()))).toList();
        return new AttemptHistoryPage(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    /** Appends an event in the same transaction as the learning update. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserProblemAttempt recordReview(User user, Problem problem, AttemptOutcome outcome, boolean recallOnly,
            Long recallCheckId, FailureReason reason, Integer timeTakenSec, Integer hintsUsed, String notes, FollowUpType followUpType) {
        return attempts.save(UserProblemAttempt.builder().user(user).problem(problem).outcome(outcome)
                .activityType(recallOnly ? AttemptActivityType.RECALL_REVIEW : AttemptActivityType.CODING_REVIEW)
                .isRevision(true).recallCheckId(recallCheckId).failureReason(reason).timeTakenSec(timeTakenSec)
                .hintsUsed(hintsUsed == null ? 0 : hintsUsed).notes(notes).followUpType(followUpType).attemptedAt(timeService.now()).build());
    }

    @Transactional(readOnly = true)
    public ProblemNoteDto getNote(Long userId, Long problemId) {
        if (!problems.existsById(problemId)) throw new ResourceNotFoundException("Problem not found: " + problemId);
        return new ProblemNoteDto(problemId, statuses.findByUserIdAndProblemId(userId, problemId).map(UserProblemStatus::getInsightNote).orElse(null));
    }

    @Transactional
    public ProblemNoteDto updateNote(Long userId, Long problemId, String note) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Problem problem = problems.findById(problemId).orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + problemId));
        var status = statuses.findByUserIdAndProblemId(userId, problemId).orElseGet(() -> UserProblemStatus.builder().user(user).problem(problem).build());
        status.setInsightNote(note);
        statuses.save(status);
        return new ProblemNoteDto(problemId, note);
    }
}
