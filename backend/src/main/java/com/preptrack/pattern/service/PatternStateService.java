package com.preptrack.pattern.service;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.auth.entity.User;
import com.preptrack.pattern.dto.PatternUpdateDto;
import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.problem.entity.Problem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatternStateService {

    private final UserPatternStateRepository userPatternStateRepository;
    private final ProblemPatternRepository problemPatternRepository;

    @Transactional
    public UserPatternState getOrCreatePatternState(User user, Pattern pattern) {
        return userPatternStateRepository.findByUserIdAndPatternId(user.getId(), pattern.getId())
                .orElseGet(() -> {
                    UserPatternState newState = UserPatternState.builder()
                            .user(user)
                            .pattern(pattern)
                            .skill(0.0)
                            .stability(1.0)
                            .lastPracticedAt(null)
                            .totalAttempts(0)
                            .independentSolves(0)
                            .interviewBoost(0.0)
                            .build();
                    return userPatternStateRepository.save(newState);
                });
    }

    @Transactional
    public List<PatternUpdateDto> updatePatternStatesOnAttempt(User user, Problem problem, AttemptOutcome outcome) {
        List<ProblemPattern> problemPatterns = problemPatternRepository.findByProblemIdWithPattern(problem.getId());
        List<PatternUpdateDto> updates = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (ProblemPattern pp : problemPatterns) {
            Pattern pattern = pp.getPattern();
            double linkWeight = pp.getWeight() != null ? pp.getWeight() : 1.0;
            double delta = 0.1 * linkWeight;

            UserPatternState state = getOrCreatePatternState(user, pattern);
            double skillBefore = state.getSkill() != null ? state.getSkill() : 0.0;
            double stability = state.getStability() != null ? state.getStability() : 1.0;

            if (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY) {
                state.setSkill(Math.min(1.0, skillBefore + delta));
                state.setStability(Math.min(365.0, stability * 1.5));
                state.setIndependentSolves((state.getIndependentSolves() != null ? state.getIndependentSolves() : 0) + 1);
            } else if (outcome == AttemptOutcome.SOLVED_WITH_HELP) {
                state.setSkill(Math.min(1.0, skillBefore + delta * 0.3));
                // stability unchanged — help doesn't prove retention
            } else if (outcome == AttemptOutcome.COULD_NOT_SOLVE) {
                state.setSkill(Math.max(0.0, skillBefore - delta * 0.2));
                state.setStability(Math.max(1.0, stability * 0.5));
            }

            state.setLastPracticedAt(now);
            state.setTotalAttempts((state.getTotalAttempts() != null ? state.getTotalAttempts() : 0) + 1);

            UserPatternState saved = userPatternStateRepository.save(state);

            updates.add(PatternUpdateDto.builder()
                    .patternId(pattern.getId())
                    .patternName(pattern.getName())
                    .skillBefore(Math.round(skillBefore * 100.0) / 100.0)
                    .skillAfter(Math.round(saved.getSkill() * 100.0) / 100.0)
                    .retentionNow(1.0) // practiced today
                    .build());
        }

        return updates;
    }

    @Transactional(readOnly = true)
    public List<UserPatternState> getUserPatternStates(Long userId) {
        return userPatternStateRepository.findByUserIdWithPattern(userId);
    }
}
