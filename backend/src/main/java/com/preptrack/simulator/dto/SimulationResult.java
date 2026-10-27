package com.preptrack.simulator.dto;

import com.preptrack.config.EngineProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SimulationResult(UUID runId, Instant createdAt, String modelVersion, SimulationConfig config,
                               String sheetName, int problemCount, int patternCount,
                               List<StrategyResult> results, List<Comparison> comparisons,
                               List<String> caveats, EngineProperties enginePolicy, String textSummary) {
    public record Metric(double mean, double stdDev) {}
    public record DailySnapshot(int day, Metric retention, Metric retainedSkill) {}
    public record StrategyResult(String strategy, String forgettingCurve, Metric retention,
                                 Metric retainedSkill, Metric coverage, Metric minutesStudied,
                                 List<DailySnapshot> dailySnapshots) {}
    public record Comparison(String forgettingCurve, String baseline, double retentionDifference,
                             double pairedDifferenceStdDev, boolean prepTrackHigher) {}
}
