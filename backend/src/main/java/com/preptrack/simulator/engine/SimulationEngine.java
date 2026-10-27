package com.preptrack.simulator.engine;

import com.preptrack.simulator.dto.SimulationConfig;
import com.preptrack.simulator.dto.SimulationResult;
import com.preptrack.config.EngineProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import static com.preptrack.simulator.dto.SimulationResult.*;

@Component
@RequiredArgsConstructor
public class SimulationEngine {
    private final PrepTrackStrategy prepTrack;
    private final EngineProperties policy;
    public static final String MODEL_VERSION = "1.0";
    public static final List<String> CAVEATS = List.of(
            "These results compare planners under stated forgetting-curve assumptions with distribution-sampled learner parameters. Real humans vary; synthetic results do not establish a real-world advantage.",
            "Power-law forgetting is a model-mismatch test. Differences may shrink or reverse; wins are measured, never guaranteed. A small real-world pilot provides complementary evidence.",
            "Learning rate ~ LogNormal(-2.3, 0.5); initial stability ~ LogNormal(0, 0.5). Hidden skill uses diminishing gains from independent, assisted, and failed study (1, 0.65, 0.2), with quarter-size recall gains. Stability grows with sampled learning rate and feedback.",
            "All strategies share the catalog, learner parameters, event noise, time budget, and daily caps. Sheet order has no revisions; random uses seven-day reviews; SM-2 is per-problem with fixed ease 2.5. PrepTrack uses production scoring, observation updates, review costs, and knapsack with offline candidate collection; no interviews or prerequisite substitution are simulated.",
            "Retention is averaged over ALL catalog patterns, including zero for untouched patterns, 24 hours after each study day. Retained skill is skill × retention. Coverage counts distinct problems solved independently or with help. Standard deviations are sample deviations across learners, not confidence intervals."
    );

    public SimulationResult run(SimulationConfig config, SimulationCatalog catalog) {
        List<StrategyResult> results = new ArrayList<>();
        List<Comparison> comparisons = new ArrayList<>();
        for (ForgettingCurve curve : List.of(new ExponentialForgetting(), new PowerLawForgetting())) {
            Map<String, double[]> finalRetentions = new LinkedHashMap<>();
            for (PlannerStrategy strategy : List.of(
                    new BaselinePlanner(BaselinePlanner.Kind.SHEET_ORDER, config.seed()),
                    new BaselinePlanner(BaselinePlanner.Kind.RANDOM, config.seed()),
                    new BaselinePlanner(BaselinePlanner.Kind.PLAIN_SM2, config.seed()), prepTrack)) {
                double[][] retentions = new double[config.days()][config.learnerCount()];
                double[][] skills = new double[config.days()][config.learnerCount()];
                double[] coverage = new double[config.learnerCount()];
                double[] minutes = new double[config.learnerCount()];
                for (int l = 0; l < config.learnerCount(); l++) {
                    SyntheticLearner learner = new SyntheticLearner(catalog, curve, config.seed() + l * 104729L, policy);
                    for (int day = 1; day <= config.days(); day++) {
                        var plan = strategy.generatePlan(catalog, learner, config, day);
                        int spent = plan.stream().mapToInt(SyntheticLearner.StudyItem::minutes).sum();
                        long newCount = plan.stream().filter(i -> !i.revision()).count();
                        if (spent > config.dailyBudgetMinutes() || newCount > config.maxNewProblems()
                                || plan.size() - newCount > config.maxRevisions()
                                || plan.stream().map(i -> i.problem().getId()).distinct().count() != plan.size()) {
                            throw new IllegalStateException("Simulation strategy violated daily limits: " + strategy.name());
                        }
                        for (var item : plan) strategy.observe(learner, learner.study(item, day));
                        retentions[day - 1][l] = learner.averageRetention(day + 1);
                        skills[day - 1][l] = learner.retainedSkill(day + 1);
                    }
                    coverage[l] = learner.coverage();
                    minutes[l] = learner.minutesStudied();
                }
                List<DailySnapshot> snapshots = new ArrayList<>();
                for (int day = 0; day < config.days(); day++) {
                    snapshots.add(new DailySnapshot(day + 1, metric(retentions[day]), metric(skills[day])));
                }
                double[] last = retentions[config.days() - 1];
                finalRetentions.put(strategy.name(), last);
                results.add(new StrategyResult(strategy.name(), curve.name(), metric(last),
                        metric(skills[config.days() - 1]), metric(coverage), metric(minutes), List.copyOf(snapshots)));
            }
            double[] preptrack = finalRetentions.get("PREPTRACK");
            finalRetentions.forEach((strategy, retention) -> {
                if (!strategy.equals("PREPTRACK")) {
                    double[] differences = new double[retention.length];
                    for (int i = 0; i < retention.length; i++) differences[i] = preptrack[i] - retention[i];
                    Metric delta = metric(differences);
                    comparisons.add(new Comparison(curve.name(), strategy, delta.mean(), delta.stdDev(), delta.mean() > 0));
                }
            });
        }
        EngineProperties policySnapshot = policy.snapshot();
        StringBuilder report = new StringBuilder(String.format(Locale.ROOT,
                "PrepTrack simulator v%s%n%d days; %d paired learners; %d min/day; seed %d; %s (%d problems, %d patterns)%n"
                        + "Retention: mean ± sample standard deviation; evaluated 24h after study.%n",
                MODEL_VERSION, config.days(), config.learnerCount(), config.dailyBudgetMinutes(), config.seed(),
                catalog.sheetName(), catalog.problems().size(), catalog.patterns().size()));
        report.append("Engine policy parameters: ").append(policySnapshot).append('\n');
        for (StrategyResult result : results) {
            report.append(String.format(Locale.ROOT, "%s / %s: retention %.2f%% ± %.2f%%; retained skill %.2f%%; coverage %.2f%%; minutes %.1f%n",
                    result.forgettingCurve(), result.strategy(), result.retention().mean() * 100, result.retention().stdDev() * 100,
                    result.retainedSkill().mean() * 100, result.coverage().mean() * 100, result.minutesStudied().mean()));
        }
        for (Comparison comparison : comparisons) {
            report.append(String.format(Locale.ROOT, "%s: PrepTrack vs %s = %+.2f percentage points (paired std %.2f pp).%n",
                    comparison.forgettingCurve(), comparison.baseline(), comparison.retentionDifference() * 100,
                    comparison.pairedDifferenceStdDev() * 100));
        }
        CAVEATS.forEach(c -> report.append("\nCaveat: ").append(c));
        return new SimulationResult(UUID.randomUUID(), Instant.now(), MODEL_VERSION, config, catalog.sheetName(),
                catalog.problems().size(), catalog.patterns().size(), List.copyOf(results), List.copyOf(comparisons), CAVEATS, policySnapshot, report.toString());
    }

    public static Metric metric(double[] values) {
        double mean = Arrays.stream(values).average().orElse(0);
        double sum = Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum();
        return new Metric(mean, values.length > 1 ? Math.sqrt(sum / (values.length - 1)) : 0);
    }
}
