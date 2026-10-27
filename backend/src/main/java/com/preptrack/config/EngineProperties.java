package com.preptrack.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.entity.UserSettings;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "preptrack.engine")
public class EngineProperties {

    @NotBlank private String policyVersion = "1";
    @Min(15) @Max(480) private int defaultBudgetMinutes = 90;
    @Min(1) @Max(20) private int defaultMaxNewProblems = 3;
    @Min(1) @Max(50) private int defaultMaxRevisions = 10;
    @DecimalMin("0.0") @DecimalMax("1.0") private double defaultTargetSkill = 0.8;

    @DecimalMin("0.0") @DecimalMax("1.0") private double independentSolveSkillGain = 0.10;
    // Preserve the active behavior, rather than the previously unused .05/.05 declarations.
    @DecimalMin("0.0") @DecimalMax("1.0") private double withHelpSkillGain = 0.03;
    @DecimalMin("0.0") @DecimalMax("1.0") private double couldNotSolveSkillDecay = 0.02;
    @DecimalMin("0.01") @DecimalMax("10.0") private double independentSolveStabilityMultiplier = 1.50;
    @DecimalMin("0.01") @DecimalMax("10.0") private double withHelpStabilityMultiplier = 1.0;
    @DecimalMin("0.01") @DecimalMax("1.0") private double failureStabilityMultiplier = 0.50;
    @DecimalMin("0.01") @DecimalMax("3650.0") private double initialStability = 1.0;
    @DecimalMin("0.01") @DecimalMax("3650.0") private double maxStability = 365.0;

    @DecimalMin("0.0") @DecimalMax("1.0") private double maintenanceFloor = 0.15;
    @DecimalMin("0.0") @DecimalMax("100.0") private double retryBoostMultiplier = 2.0;
    @DecimalMin("0.0") @DecimalMax("1.0") private double diversityMultiplier = 0.5;
    @DecimalMin("0.0") @DecimalMax("100.0") private double prerequisiteMultiplier = 0.8;
    @DecimalMin("0.0") @DecimalMax("100.0") private double codeFromScratchMultiplier = 1.5;
    @DecimalMin("0.0") @DecimalMax("100.0") private double edgeCaseMultiplier = 1.2;
    @DecimalMin("0.0") @DecimalMax("100.0") private double timedRetryMultiplier = 1.3;
    @DecimalMin("0.0") @DecimalMax("1.0") private double overdueBonusPerDay = 0.05;
    @DecimalMin("0.0") @DecimalMax("10.0") private double maxOverdueBonus = 0.5;

    @Min(1) @Max(365) private int interviewProximityWindowDays = 14;
    @DecimalMin("0.0") @DecimalMax("10.0") private double interviewProximityMaxBoost = 0.5;
    @Min(1) @Max(365) private int interviewBoostDecayDays = 14;
    @DecimalMin("0.0") @DecimalMax("10.0") private double topicConfirmedBoost = 0.3;
    @DecimalMin("0.0") @DecimalMax("10.0") private double maxInterviewBoost = 0.6;

    @Min(1) @Max(480) private int recallMinutes = 5;
    @Min(1) @Max(480) private int defaultProblemMinutes = 30;
    @Min(1) @Max(480) private int edgeCaseMinimumMinutes = 10;
    @DecimalMin("0.01") @DecimalMax("1.0") private double edgeCaseTimeFraction = 0.5;
    @Min(1) @Max(365) private int revisionRetryDays = 1;
    @NotEmpty @Size(max = 20) private List<@NotNull @Min(1) @Max(3650) Integer> revisionIntervals = List.of(3, 7, 14, 30);
    @DecimalMin(value = "0.0", inclusive = false) @DecimalMax(value = "1.0", inclusive = false)
    private double revisionRetentionTarget = 0.70;
    @DecimalMin("0.01") @DecimalMax("3650.0") private double revisionFallbackStability = 8.4;

    @AssertTrue(message = "maxStability must be at least initialStability")
    @JsonIgnore
    public boolean isStabilityRangeValid() { return maxStability >= initialStability; }

    @AssertTrue(message = "maxInterviewBoost must be at least topicConfirmedBoost")
    @JsonIgnore
    public boolean isInterviewBoostRangeValid() { return maxInterviewBoost >= topicConfirmedBoost; }

    @AssertTrue(message = "revisionIntervals must be positive and nondecreasing")
    @JsonIgnore
    public boolean isRevisionIntervalOrderValid() {
        if (revisionIntervals == null || revisionIntervals.isEmpty()) return false;
        for (int i = 0; i < revisionIntervals.size(); i++) {
            if (revisionIntervals.get(i) == null || revisionIntervals.get(i) <= 0
                    || (i > 0 && revisionIntervals.get(i) < revisionIntervals.get(i - 1))) return false;
        }
        return true;
    }

    public int revisionIntervalDays(int successfulReviews) {
        return revisionIntervals.get(Math.max(0, Math.min(revisionIntervals.size() - 1, successfulReviews - 1)));
    }

    public UserSettings defaultSettings(User user) {
        return UserSettings.builder().user(user).dailyTimeBudgetMin(defaultBudgetMinutes)
                .maxNewProblemsDay(defaultMaxNewProblems).maxRevisionsDay(defaultMaxRevisions)
                .targetSkillLevel(defaultTargetSkill).build();
    }

    public EngineProperties snapshot() {
        var copy = new EngineProperties();
        BeanUtils.copyProperties(this, copy);
        copy.setRevisionIntervals(List.copyOf(revisionIntervals));
        return copy;
    }
}
