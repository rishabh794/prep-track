package com.preptrack.pattern.service;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.config.EngineProperties;
import java.time.LocalDateTime;

/** The planner's observation update, also used by the offline simulator's estimated states. */
public final class PatternLearningModel {
    private PatternLearningModel() {}

    public static void apply(UserPatternState state, AttemptOutcome outcome, double weight,
                             boolean recallOnly, LocalDateTime practicedAt) {
        apply(state, outcome, weight, recallOnly, practicedAt, new EngineProperties());
    }

    public static void apply(UserPatternState state, AttemptOutcome outcome, double weight,
                             boolean recallOnly, LocalDateTime practicedAt, EngineProperties policy) {
        double skill = state.getSkill() == null ? 0 : state.getSkill();
        double stability = state.getStability() == null ? policy.getInitialStability() : state.getStability();
        if (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY) {
            if (!recallOnly) state.setSkill(Math.min(1, skill + policy.getIndependentSolveSkillGain() * weight));
            state.setStability(Math.min(policy.getMaxStability(), stability * policy.getIndependentSolveStabilityMultiplier()));
            state.setIndependentSolves((state.getIndependentSolves() == null ? 0 : state.getIndependentSolves()) + 1);
        } else if (outcome == AttemptOutcome.SOLVED_WITH_HELP) {
            state.setSkill(Math.min(1, skill + policy.getWithHelpSkillGain() * weight));
            state.setStability(Math.min(policy.getMaxStability(), Math.max(policy.getInitialStability(), stability * policy.getWithHelpStabilityMultiplier())));
        } else {
            state.setSkill(Math.max(0, skill - policy.getCouldNotSolveSkillDecay() * weight));
            state.setStability(Math.min(policy.getMaxStability(), Math.max(policy.getInitialStability(), stability * policy.getFailureStabilityMultiplier())));
        }
        state.setLastPracticedAt(practicedAt);
        state.setTotalAttempts((state.getTotalAttempts() == null ? 0 : state.getTotalAttempts()) + 1);
    }
}
