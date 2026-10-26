package com.preptrack.pattern.service;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.pattern.entity.UserPatternState;
import java.time.LocalDateTime;

/** The planner's observation update, also used by the offline simulator's estimated states. */
public final class PatternLearningModel {
    private PatternLearningModel() {}

    public static void apply(UserPatternState state, AttemptOutcome outcome, double weight,
                             boolean recallOnly, LocalDateTime practicedAt) {
        double skill = state.getSkill() == null ? 0 : state.getSkill();
        double stability = state.getStability() == null ? 1 : state.getStability();
        if (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY) {
            if (!recallOnly) state.setSkill(Math.min(1, skill + 0.1 * weight));
            state.setStability(Math.min(365, stability * 1.5));
            state.setIndependentSolves((state.getIndependentSolves() == null ? 0 : state.getIndependentSolves()) + 1);
        } else if (outcome == AttemptOutcome.SOLVED_WITH_HELP) {
            state.setSkill(Math.min(1, skill + 0.03 * weight));
        } else {
            state.setSkill(Math.max(0, skill - 0.02 * weight));
            state.setStability(Math.max(1, stability * 0.5));
        }
        state.setLastPracticedAt(practicedAt);
        state.setTotalAttempts((state.getTotalAttempts() == null ? 0 : state.getTotalAttempts()) + 1);
    }
}
