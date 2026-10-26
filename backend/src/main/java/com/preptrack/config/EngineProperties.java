package com.preptrack.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "preptrack.engine")
public class EngineProperties {

    // Default User Settings
    private int defaultBudgetMinutes = 90;
    private int defaultMaxNewProblems = 3;
    private int defaultMaxRevisions = 10;
    private double defaultTargetSkill = 0.8;

    // Pattern Learning Constants
    private double independentSolveSkillGain = 0.10;
    private double withHelpSkillGain = 0.05;
    private double couldNotSolveSkillDecay = 0.05;
    private double independentSolveStabilityMultiplier = 1.50;
    private double withHelpStabilityMultiplier = 1.10;
    private double failureStabilityMultiplier = 0.50;

    // Planner Constants
    private double maintenanceFloor = 0.15;
    private double retryBoostMultiplier = 2.0;
    private double diversityMultiplier = 0.5;
    private int interviewProximityWindowDays = 14;
    private double interviewProximityMaxBoost = 0.5;
    private double topicConfirmedBoost = 0.3;
    private double maxInterviewBoost = 0.6;
}
