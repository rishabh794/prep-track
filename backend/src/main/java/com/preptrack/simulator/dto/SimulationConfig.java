package com.preptrack.simulator.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record SimulationConfig(
        @Min(1) @Max(60) Integer days,
        @Min(1) @Max(100) Integer learnerCount,
        @Min(5) @Max(240) Integer dailyBudgetMinutes,
        @Min(0) @Max(5) Integer maxNewProblems,
        @Min(0) @Max(10) Integer maxRevisions,
        @DecimalMin("0.1") @DecimalMax("1.0") Double targetSkill,
        Long seed,
        @Positive Long sheetId
) {
    public SimulationConfig {
        days = days == null ? 30 : days;
        learnerCount = learnerCount == null ? 50 : learnerCount;
        dailyBudgetMinutes = dailyBudgetMinutes == null ? 90 : dailyBudgetMinutes;
        maxNewProblems = maxNewProblems == null ? 3 : maxNewProblems;
        maxRevisions = maxRevisions == null ? 10 : maxRevisions;
        targetSkill = targetSkill == null ? 0.8 : targetSkill;
        seed = seed == null ? 42L : seed;
    }

    @AssertTrue(message = "Choose at least one new-problem or revision slot")
    @JsonIgnore
    public boolean isStudyEnabled() {
        return maxNewProblems > 0 || maxRevisions > 0;
    }

    @AssertTrue(message = "Run too large; reduce days, learners, budget, or daily slots")
    @JsonIgnore
    public boolean isWorkloadBounded() {
        return (long) days * learnerCount * dailyBudgetMinutes * (maxNewProblems + 1) * (maxRevisions + 1)
                <= 12_000_000L;
    }
}
