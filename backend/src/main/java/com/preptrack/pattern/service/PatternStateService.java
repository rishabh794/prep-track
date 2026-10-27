package com.preptrack.pattern.service;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.auth.entity.User;
import com.preptrack.config.EngineProperties;
import com.preptrack.common.service.TimeService;
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

import com.preptrack.pattern.dto.PatternStateResponseDto;
import com.preptrack.pattern.repository.PatternRepository;
import com.preptrack.planner.engine.RetentionCalculator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatternStateService {

    private final UserPatternStateRepository userPatternStateRepository;
    private final ProblemPatternRepository problemPatternRepository;
    private final PatternRepository patternRepository;
    private final RetentionCalculator retentionCalculator;
    private final EngineProperties policy;
    private final TimeService timeService;

    @Transactional
    public UserPatternState getOrCreatePatternState(User user, Pattern pattern) {
        return userPatternStateRepository.findByUserIdAndPatternId(user.getId(), pattern.getId())
                .orElseGet(() -> {
                    UserPatternState newState = UserPatternState.builder()
                            .user(user)
                            .pattern(pattern)
                            .skill(0.0)
                            .stability(policy.getInitialStability())
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
        LocalDateTime now = timeService.now();

        for (ProblemPattern pp : problemPatterns) {
            Pattern pattern = pp.getPattern();
            double linkWeight = pp.getWeight() != null ? pp.getWeight() : 1.0;

            UserPatternState state = getOrCreatePatternState(user, pattern);
            double skillBefore = state.getSkill() != null ? state.getSkill() : 0.0;
            PatternLearningModel.apply(state, outcome, linkWeight, false, now, policy);

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

    @Transactional
    public List<PatternUpdateDto> updatePatternStatesOnRecallGrade(User user, Problem problem, com.preptrack.ai.entity.RecallGrade grade) {
        List<ProblemPattern> problemPatterns = problemPatternRepository.findByProblemIdWithPattern(problem.getId());
        List<PatternUpdateDto> updates = new ArrayList<>();
        LocalDateTime now = timeService.now();

        for (ProblemPattern pp : problemPatterns) {
            Pattern pattern = pp.getPattern();
            double linkWeight = pp.getWeight() != null ? pp.getWeight() : 1.0;

            UserPatternState state = getOrCreatePatternState(user, pattern);
            double skillBefore = state.getSkill() != null ? state.getSkill() : 0.0;
            AttemptOutcome observedOutcome = switch (grade) {
                case PASS -> AttemptOutcome.SOLVED_INDEPENDENTLY;
                case PARTIAL -> AttemptOutcome.SOLVED_WITH_HELP;
                case FAIL -> AttemptOutcome.COULD_NOT_SOLVE;
            };
            PatternLearningModel.apply(state, observedOutcome, linkWeight, true, now, policy);

            UserPatternState saved = userPatternStateRepository.save(state);

            updates.add(PatternUpdateDto.builder()
                    .patternId(pattern.getId())
                    .patternName(pattern.getName())
                    .skillBefore(Math.round(skillBefore * 100.0) / 100.0)
                    .skillAfter(Math.round(saved.getSkill() * 100.0) / 100.0)
                    .retentionNow(1.0)
                    .build());
        }

        return updates;
    }

    @Transactional(readOnly = true)
    public List<UserPatternState> getUserPatternStates(Long userId) {
        return userPatternStateRepository.findByUserIdWithPattern(userId);
    }

    @Transactional(readOnly = true)
    public List<PatternStateResponseDto> getUserPatternStateDtos(Long userId) {
        List<Pattern> allPatterns = patternRepository.findAll();
        List<UserPatternState> states = userPatternStateRepository.findByUserIdWithPattern(userId);
        Map<Long, UserPatternState> stateMap = states.stream()
                .collect(Collectors.toMap(s -> s.getPattern().getId(), s -> s));

        return allPatterns.stream().map(p -> {
            UserPatternState state = stateMap.get(p.getId());
            double skill = state != null && state.getSkill() != null ? state.getSkill() : 0.0;
            double stability = state != null && state.getStability() != null ? state.getStability() : policy.getInitialStability();
            var practiced = state != null && state.getLastPracticedAt() != null
                    ? timeService.getPracticeDate(state.getLastPracticedAt(), userId).atStartOfDay() : null;
            double retention = retentionCalculator.compute(practiced, stability, timeService.getLocalDate(userId).atStartOfDay());

            return PatternStateResponseDto.builder()
                    .patternId(p.getId())
                    .name(p.getName())
                    .slug(p.getSlug())
                    .parentTopic(p.getParentTopic())
                    .skill(Math.round(skill * 100.0) / 100.0)
                    .retention(Math.round(retention * 100.0) / 100.0)
                    .stability(Math.round(stability * 10.0) / 10.0)
                    .lastPracticedAt(state != null ? state.getLastPracticedAt() : null)
                    .totalAttempts(state != null && state.getTotalAttempts() != null ? state.getTotalAttempts() : 0)
                    .independentSolves(state != null && state.getIndependentSolves() != null ? state.getIndependentSolves() : 0)
                    .build();
        }).toList();
    }
}
