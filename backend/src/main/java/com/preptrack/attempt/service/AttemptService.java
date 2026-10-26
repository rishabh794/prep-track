package com.preptrack.attempt.service;

import com.preptrack.attempt.dto.AttemptResponseDto;
import com.preptrack.attempt.dto.CreateAttemptRequest;
import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.attempt.entity.ProblemStatus;
import com.preptrack.attempt.entity.UserProblemAttempt;
import com.preptrack.attempt.entity.UserProblemStatus;
import com.preptrack.attempt.repository.UserProblemAttemptRepository;
import com.preptrack.attempt.repository.UserProblemStatusRepository;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.dto.PatternUpdateDto;
import com.preptrack.pattern.service.PatternStateService;
// import com.preptrack.planner.entity.DailyPlanItem;
// import com.preptrack.planner.repository.DailyPlanItemRepository;
import com.preptrack.problem.entity.Problem;
import com.preptrack.problem.repository.ProblemRepository;
import com.preptrack.revision.dto.RevisionScheduledDto;
import com.preptrack.revision.entity.FollowUpType;
import com.preptrack.revision.service.RevisionService;
import com.preptrack.sheet.repository.SheetProblemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptService {

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final UserProblemAttemptRepository userProblemAttemptRepository;
    private final UserProblemStatusRepository userProblemStatusRepository;
    private final PatternStateService patternStateService;
    private final RevisionService revisionService;
    private final FollowUpAssigner followUpAssigner;
    private final SheetProblemRepository sheetProblemRepository;
    // private final DailyPlanItemRepository dailyPlanItemRepository;

    @Transactional
    public AttemptResponseDto recordAttempt(String userEmail, CreateAttemptRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + userEmail));

        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + request.getProblemId()));

        LocalDateTime now = LocalDateTime.now();
        FollowUpAssignment followUp = null;
        if (request.getOutcome() != AttemptOutcome.SOLVED_INDEPENDENTLY && request.getFailureReason() != null) {
            followUp = followUpAssigner.assign(problem, request.getFailureReason());
        }
        FollowUpType followUpType = followUp != null ? followUp.getFollowUpType() : null;

        // 1. Insert into user_problem_attempts
        UserProblemAttempt attempt = UserProblemAttempt.builder()
                .user(user)
                .problem(problem)
                .outcome(request.getOutcome())
                .failureReason(request.getFailureReason())
                .timeTakenSec(request.getTimeTakenSec())
                .hintsUsed(request.getHintsUsed() != null ? request.getHintsUsed() : 0)
                .isRevision(Boolean.TRUE.equals(request.getIsRevision()))
                .followUpType(followUpType)
                .notes(request.getNotes())
                .attemptedAt(now)
                .build();
        UserProblemAttempt savedAttempt = userProblemAttemptRepository.save(attempt);

        // 2. Update user_problem_status
        UserProblemStatus status = userProblemStatusRepository
                .findByUserIdAndProblemId(user.getId(), problem.getId())
                .orElseGet(() -> UserProblemStatus.builder()
                        .user(user)
                        .problem(problem)
                        .status(ProblemStatus.UNSOLVED)
                        .totalAttempts(0)
                        .independentSolves(0)
                        .build());

        ProblemStatus newStatus;
        if (request.getOutcome() == AttemptOutcome.SOLVED_INDEPENDENTLY) {
            newStatus = ProblemStatus.SOLVED_INDEPENDENTLY;
            if (status.getFirstSolvedAt() == null) {
                status.setFirstSolvedAt(now);
            }
            status.setIndependentSolves((status.getIndependentSolves() != null ? status.getIndependentSolves() : 0) + 1);
        } else if (request.getOutcome() == AttemptOutcome.SOLVED_WITH_HELP) {
            newStatus = ProblemStatus.SOLVED_WITH_HELP;
            if (status.getFirstSolvedAt() == null) {
                status.setFirstSolvedAt(now);
            }
        } else {
            // COULD_NOT_SOLVE -> status = ATTEMPTED (does NOT count in progress)
            newStatus = ProblemStatus.ATTEMPTED;
        }

        status.setStatus(newStatus);
        status.setTotalAttempts((status.getTotalAttempts() != null ? status.getTotalAttempts() : 0) + 1);
        status.setLastOutcome(request.getOutcome());
        status.setLastFailureReason(request.getFailureReason());
        status.setLastAttemptedAt(now);
        if (request.getInsightNote() != null && !request.getInsightNote().isBlank()) {
            status.setInsightNote(request.getInsightNote());
        }
        userProblemStatusRepository.save(status);

        // 3. Update user_pattern_state
        List<PatternUpdateDto> patternUpdates = patternStateService.updatePatternStatesOnAttempt(
                user, problem, request.getOutcome()
        );

        // 4. Create / update revision card
        RevisionScheduledDto revisionScheduled = revisionService.scheduleRevision(
                user, problem, request.getOutcome(), followUpType
        );

        // 5. Plan item completion (integrated in planner engine)
        boolean planItemCompleted = false;
        /*
        Optional<DailyPlanItem> planItemOpt = dailyPlanItemRepository.findItemForToday(
                user.getId(), LocalDate.now(), problem.getId()
        );
        if (planItemOpt.isPresent()) {
            DailyPlanItem planItem = planItemOpt.get();
            planItem.setCompleted(true);
            planItem.setCompletedAt(now);
            planItem.setAttempt(savedAttempt);
            dailyPlanItemRepository.save(planItem);
            planItemCompleted = true;
        }
        */

        // 6. Find sheets affected
        List<String> sheetsAffected = sheetProblemRepository.findSheetNamesByProblemId(problem.getId());

        return AttemptResponseDto.builder()
                .attemptId(savedAttempt.getId())
                .statusUpdate(AttemptResponseDto.StatusUpdateDto.builder()
                        .problemId(problem.getId())
                        .newStatus(newStatus)
                        .sheetsAffected(sheetsAffected)
                        .build())
                .patternUpdates(patternUpdates)
                .revisionScheduled(revisionScheduled)
                .planItemCompleted(planItemCompleted)
                .build();
    }
}
