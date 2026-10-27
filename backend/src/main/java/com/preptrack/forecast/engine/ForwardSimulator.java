package com.preptrack.forecast.engine;

import com.preptrack.forecast.dto.PatternProjectionDto;
import com.preptrack.forecast.dto.ReadinessForecastDto;
import com.preptrack.forecast.dto.TriageStatus;
import com.preptrack.forecast.dto.ForecastStatus;
import com.preptrack.forecast.config.ForecastProperties;
import com.preptrack.config.EngineProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.*;

@Component
public class ForwardSimulator {

    public static final String MODEL = "PATTERN_APPROXIMATION";
    public static final String MODEL_VERSION = "2.0";
    public static final List<String> LIMITATIONS = List.of(
            "This is a pattern-level approximation, not a replay of the daily knapsack planner or a probability of passing an interview.",
            "Future study uses deterministic learning assumptions and exponential retention; real completion, success rates, and memory vary.",
            "New-problem time uses the current average estimate of available problems in each pattern. Multi-pattern problems can appear in more than one pattern pool.",
            "Company weights are planning preferences, not verified question frequencies; low weights alone do not establish that a topic will be absent."
    );
    private final EngineProperties engine;
    private final ForecastProperties properties;

    @Autowired
    public ForwardSimulator(EngineProperties engine, ForecastProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    public ForwardSimulator() {
        this(new EngineProperties(), new ForecastProperties());
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternSnapshot {
        private String patternName;
        private double currentSkill;
        private double currentStability;
        private Integer daysSinceLastPracticed; // null if never practiced
        private int availableProblems;
        private double companyRelevance; // 1.0 default, lower for low-frequency
        private Integer estimatedProblemMinutes;
    }

    public static class SimState {
        String patternName;
        double initialSkill;
        double initialStability;
        Integer daysSinceLastPracticed;
        int availableProblems;
        double companyRelevance;
        int estimatedProblemMinutes;

        double simSkill;
        double simStability;
        Integer simLastPracticedDay; // relative day (1..N) or null

        public SimState(PatternSnapshot snapshot, double initialStability) {
            this.patternName = snapshot.getPatternName();
            this.initialSkill = snapshot.getCurrentSkill();
            this.initialStability = snapshot.getCurrentStability() <= 0.0 ? initialStability : snapshot.getCurrentStability();
            this.daysSinceLastPracticed = snapshot.getDaysSinceLastPracticed();
            this.availableProblems = snapshot.getAvailableProblems();
            this.companyRelevance = snapshot.getCompanyRelevance() > 0 ? snapshot.getCompanyRelevance() : 1.0;
            this.estimatedProblemMinutes = snapshot.getEstimatedProblemMinutes() == null ? 0 : snapshot.getEstimatedProblemMinutes();

            this.simSkill = this.initialSkill;
            this.simStability = this.initialStability;
            this.simLastPracticedDay = null;
        }

        public double getRetentionAtDay(int currentDay) {
            if (simLastPracticedDay != null) {
                int daysSince = currentDay - simLastPracticedDay;
                return Math.exp(-(double) daysSince / simStability);
            }
            if (daysSinceLastPracticed == null) {
                return 0.0;
            }
            int daysSince = daysSinceLastPracticed + currentDay;
            return Math.exp(-(double) daysSince / simStability);
        }
    }

    public ReadinessForecastDto simulate(
            Long interviewId,
            LocalDate interviewDate,
            String companyName,
            long daysRemaining,
            int dailyBudgetMin,
            int maxNewProblemsDay,
            int maxRevisionsDay,
            double targetSkill,
            List<PatternSnapshot> snapshots
    ) {
        long effectiveDays = Math.max(0, Math.min(daysRemaining, properties.getMaxHorizonDays()));
        int budget = dailyBudgetMin > 0 ? dailyBudgetMin : engine.getDefaultBudgetMinutes();
        int maxNew = Math.max(0, maxNewProblemsDay);
        int maxRev = Math.max(0, maxRevisionsDay);
        double target = Math.max(0.0, Math.min(1.0, targetSkill));
        int recallMinutes = engine.getRecallMinutes();

        List<SimState> simStates = new ArrayList<>();
        for (PatternSnapshot p : snapshots) {
            simStates.add(new SimState(p, engine.getInitialStability()));
        }

        // Simulate day-by-day for d = 1..effectiveDays
        for (int d = 1; d <= effectiveDays; d++) {
            int remainingTime = budget;

            // 1. Revision Phase: patterns with retention < 0.70
            List<SimState> revisionCandidates = new ArrayList<>();
            for (SimState s : simStates) {
                if (s.simLastPracticedDay != null || s.daysSinceLastPracticed != null) {
                    double ret = s.getRetentionAtDay(d);
                    if (ret < properties.getReviewTriggerRetention()) {
                        revisionCandidates.add(s);
                    }
                }
            }

            // Sort by lowest retention first
            final int currentDay = d;
            revisionCandidates.sort(Comparator.comparingDouble(s -> s.getRetentionAtDay(currentDay)));

            int revisionsDone = 0;
            for (SimState s : revisionCandidates) {
                if (revisionsDone >= maxRev || remainingTime < recallMinutes) {
                    break;
                }
                // Perform revision (5 min baseline)
                s.simStability = Math.min(engine.getMaxStability(), s.simStability * properties.getRevisionStabilityMultiplier());
                s.simSkill = Math.min(1.0, s.simSkill + properties.getRevisionSkillGain());
                s.simLastPracticedDay = d;
                remainingTime -= recallMinutes;
                revisionsDone++;
            }

            // 2. Acquisition Phase: new problems for patterns below target skill
            List<SimState> acquisitionCandidates = new ArrayList<>();
            for (SimState s : simStates) {
                if (s.simSkill < target && s.availableProblems > 0) {
                    acquisitionCandidates.add(s);
                }
            }

            // Sort by learning priority: (target - skill) * companyRelevance
            acquisitionCandidates.sort((a, b) -> {
                double prioA = (target - a.simSkill) * a.companyRelevance;
                double prioB = (target - b.simSkill) * b.companyRelevance;
                return Double.compare(prioB, prioA);
            });

            int newProblemsDone = 0;
            for (SimState s : acquisitionCandidates) {
                if (newProblemsDone >= maxNew) {
                    break;
                }
                int minutes = s.estimatedProblemMinutes > 0 ? s.estimatedProblemMinutes : engine.getDefaultProblemMinutes();
                if (remainingTime < minutes) continue;
                // Perform new problem solve
                s.simSkill = Math.min(1.0, s.simSkill + engine.getIndependentSolveSkillGain());
                s.simStability = Math.min(engine.getMaxStability(), s.simStability * properties.getNewProblemStabilityMultiplier());
                s.simLastPracticedDay = d;
                s.availableProblems--;
                remainingTime -= minutes;
                newProblemsDone++;
            }
        }

        // Project pattern states at interview date
        List<PatternProjectionDto> projections = new ArrayList<>();
        double totalWeightedReadiness = 0.0;
        double totalWeight = 0.0;

        for (SimState s : simStates) {
            double currentRetention = s.daysSinceLastPracticed == null ? 0.0 :
                    Math.exp(-(double) s.daysSinceLastPracticed / s.initialStability);
            double projectedRetention = s.getRetentionAtDay((int) effectiveDays);

            double curSkillRound = Math.round(s.initialSkill * 100.0) / 100.0;
            double projSkillRound = Math.round(s.simSkill * 100.0) / 100.0;
            double curRetRound = Math.round(currentRetention * 100.0) / 100.0;
            double projRetRound = Math.round(projectedRetention * 100.0) / 100.0;

            TriageStatus status = classifyStatus(
                    s,
                    projSkillRound,
                    projRetRound,
                    effectiveDays,
                    target
            );

            projections.add(PatternProjectionDto.builder()
                    .pattern(s.patternName)
                    .currentSkill(curSkillRound)
                    .projectedSkill(projSkillRound)
                    .currentRetention(curRetRound)
                    .projectedRetention(projRetRound)
                    .companyRelevance(s.companyRelevance)
                    .status(status)
                    .build());

            double patternReadiness = projSkillRound * projRetRound;
            totalWeightedReadiness += patternReadiness * s.companyRelevance;
            totalWeight += s.companyRelevance;
        }

        // Sort projections: AT_RISK first, then CONSIDER_DROPPING, then IMPROVING, then ON_TRACK
        projections.sort(Comparator.comparingInt(p -> statusSortOrder(p.getStatus())));

        double overallReadiness = totalWeight > 0
                ? Math.round((totalWeightedReadiness / totalWeight) * 100.0) / 100.0
                : 0.0;

        List<String> recommendations = generateRecommendations(projections, companyName, target);

        return ReadinessForecastDto.builder()
                .interviewId(interviewId)
                .interviewDate(interviewDate)
                .companyName(companyName != null ? companyName : "Upcoming")
                .daysRemaining(daysRemaining)
                .status(ForecastStatus.AVAILABLE)
                .dailyBudgetMinutes(budget)
                .maxNewProblemsDay(maxNew)
                .maxRevisionsDay(maxRev)
                .targetSkillLevel(target)
                .simulationDays((int) effectiveDays)
                .horizonCapped(daysRemaining > effectiveDays)
                .model(MODEL)
                .modelVersion(MODEL_VERSION)
                .policyVersion(engine.getPolicyVersion())
                .limitations(LIMITATIONS)
                .overallReadiness(overallReadiness)
                .patternProjections(projections)
                .recommendations(recommendations)
                .build();
    }

    private int statusSortOrder(TriageStatus status) {
        if (status == null) return 4;
        return switch (status) {
            case AT_RISK -> 0;
            case CONSIDER_DROPPING -> 1;
            case IMPROVING -> 2;
            case ON_TRACK -> 3;
        };
    }

    private TriageStatus classifyStatus(
            SimState s,
            double projSkill,
            double projRetention,
            long daysRemaining,
            double targetSkill
    ) {
        // 1. CONSIDER_DROPPING condition:
        // Low configured priority or low initial skill with a short horizon.
        if (s.companyRelevance <= 0.5 && projSkill < 0.40) {
            return TriageStatus.CONSIDER_DROPPING;
        }
        if (daysRemaining < 14 && s.initialSkill < 0.20 && projSkill < 0.35 && s.companyRelevance <= 0.8) {
            return TriageStatus.CONSIDER_DROPPING;
        }

        // 2. ON_TRACK condition:
        if (projSkill >= (targetSkill * 0.90) && projRetention >= 0.65) {
            return TriageStatus.ON_TRACK;
        }

        // 3. IMPROVING condition:
        if (projSkill >= 0.50 && (projSkill - s.initialSkill) >= 0.12) {
            return TriageStatus.IMPROVING;
        }

        // 4. Otherwise: AT_RISK
        return TriageStatus.AT_RISK;
    }

    private List<String> generateRecommendations(
            List<PatternProjectionDto> projections,
            String companyName,
            double targetSkill
    ) {
        List<String> recommendations = new ArrayList<>();
        String company = (companyName != null && !companyName.isBlank()) ? companyName : "this company";

        // 1. Top AT_RISK patterns
        for (PatternProjectionDto p : projections) {
            if (p.getStatus() == TriageStatus.AT_RISK && recommendations.size() < 2) {
                double gap = Math.max(0.1, targetSkill - p.getProjectedSkill());
                int sessions = Math.max(2, (int) Math.ceil(gap / 0.10));
                int targetPercent = (int) Math.round(targetSkill * 100);
                recommendations.add(String.format("Focus %d more sessions on %s to reach %d%% projected skill",
                        sessions, p.getPattern(), targetPercent));
            }
        }

        // 2. Untouched pattern kickstart
        for (PatternProjectionDto p : projections) {
            if (p.getCurrentSkill() == 0.0 && p.getStatus() == TriageStatus.AT_RISK && recommendations.size() < 3) {
                recommendations.add(String.format("Start %s this week — 2 problems min", p.getPattern()));
                break;
            }
        }

        // 3. Low configured priority is a planning preference, not measured company frequency.
        for (PatternProjectionDto p : projections) {
            if (p.getStatus() == TriageStatus.CONSIDER_DROPPING
                    && p.getCompanyRelevance() != null && p.getCompanyRelevance() <= 0.5
                    && recommendations.size() < 3) {
                recommendations.add(String.format("%s has a low configured priority for %s — consider reducing its allocation after checking your interview requirements",
                        p.getPattern(), company));
                break;
            }
        }

        // 4. ON_TRACK maintenance advice
        List<String> onTrackPatterns = projections.stream()
                .filter(p -> p.getStatus() == TriageStatus.ON_TRACK)
                .map(PatternProjectionDto::getPattern)
                .limit(2)
                .toList();
        if (!onTrackPatterns.isEmpty() && recommendations.size() < 4) {
            String names = String.join(" and ", onTrackPatterns);
            recommendations.add(names + " are on track — reduce to maintenance revisions only");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Current preparation pace is balanced. Continue daily plan revisions.");
        }

        return recommendations;
    }
}
