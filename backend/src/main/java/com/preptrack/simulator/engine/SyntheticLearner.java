package com.preptrack.simulator.engine;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.service.PatternLearningModel;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.entity.FollowUpType;
import java.time.LocalDate;
import java.util.*;

/** Hidden ground truth is deliberately different from the planner's fixed update equations. */
public final class SyntheticLearner {
    public static final LocalDate EPOCH = LocalDate.of(2020, 1, 1);
    public record StudyItem(Problem problem, boolean revision, boolean recallOnly, int minutes) {}
    public record Observation(int day, StudyItem item, AttemptOutcome outcome, FollowUpType followUp) {}
    public static final class Card {
        public AttemptOutcome outcome;
        public FollowUpType followUp;
        public int dueDay;
        public int reviews;
        public int sm2Successes;
        public int sm2Interval;
    }
    private static final class Memory {
        double skill;
        double stability;
        Integer lastDay;
        Memory(double stability) { this.stability = stability; }
    }

    private final SimulationCatalog catalog;
    private final ForgettingCurve curve;
    private final long seed;
    private final double learningRate;
    private final double initialStability;
    private final Map<Long, Memory> memory = new HashMap<>();
    private final Map<Long, UserPatternState> estimates = new HashMap<>();
    private final Map<Long, Card> cards = new HashMap<>();
    private final Set<Long> solved = new HashSet<>();
    private int minutesStudied;

    public SyntheticLearner(SimulationCatalog catalog, ForgettingCurve curve, long seed) {
        this.catalog = catalog;
        this.curve = curve;
        this.seed = seed;
        Random parameters = new Random(seed);
        learningRate = Math.exp(-2.3 + 0.5 * parameters.nextGaussian());
        initialStability = Math.exp(0.5 * parameters.nextGaussian());
        for (var pattern : catalog.patterns()) {
            memory.put(pattern.getId(), new Memory(initialStability));
            estimates.put(pattern.getId(), UserPatternState.builder().pattern(pattern).build());
        }
    }

    public Observation study(StudyItem item, int day) {
        var links = catalog.links().get(item.problem().getId());
        double weightSum = links.stream().mapToDouble(p -> p.getWeight() == null ? 1 : p.getWeight()).sum();
        double ability = 0;
        for (var link : links) {
            Memory m = memory.get(link.getPattern().getId());
            double weight = link.getWeight() == null ? 1 : link.getWeight();
            ability += weight * m.skill * (0.25 + 0.75 * retention(m, day));
        }
        ability /= Math.max(0.01, weightSum);
        double difficulty = item.recallOnly() ? 1 : switch (item.problem().getDifficulty()) {
            case EASY -> 1.0;
            case MEDIUM -> 0.7;
            case HARD -> 0.4;
        };
        // Common random numbers: the same learner/day/problem gets the same noise in every strategy and curve.
        Random event = new Random(seed ^ (day * 0x9E3779B97F4A7C15L) ^ (item.problem().getId() * 0xBF58476D1CE4E5B9L));
        double independent = clamp((0.15 + 0.85 * ability) * difficulty + 0.05 * event.nextGaussian());
        double roll = event.nextDouble();
        AttemptOutcome outcome = roll < independent ? AttemptOutcome.SOLVED_INDEPENDENTLY
                : roll < Math.min(1, independent + 0.4) ? AttemptOutcome.SOLVED_WITH_HELP : AttemptOutcome.COULD_NOT_SOLVE;
        FollowUpType followUp = outcome == AttemptOutcome.SOLVED_INDEPENDENTLY ? null
                : FollowUpType.values()[event.nextInt(FollowUpType.values().length)];
        for (var link : links) {
            Memory m = memory.get(link.getPattern().getId());
            double weight = link.getWeight() == null ? 1 : link.getWeight();
            double feedback = switch (outcome) {
                case SOLVED_INDEPENDENTLY -> 1;
                case SOLVED_WITH_HELP -> 0.65;
                case COULD_NOT_SOLVE -> 0.2;
            };
            // Diminishing learning, sampled gain, and exposure-based stability; never read planner estimates here.
            m.skill = clamp(m.skill + learningRate * weight * (1 - m.skill) * feedback * (item.recallOnly() ? 0.25 : 1));
            m.stability = Math.min(365, m.stability * (1 + learningRate * feedback * (item.recallOnly() ? 4 : 2)));
            m.lastDay = day;
            PatternLearningModel.apply(estimates.get(link.getPattern().getId()), outcome, weight,
                    item.recallOnly(), EPOCH.plusDays(day).atStartOfDay());
        }
        boolean firstReview = !cards.containsKey(item.problem().getId());
        Card card = cards.computeIfAbsent(item.problem().getId(), ignored -> new Card());
        card.reviews = switch (outcome) {
            case SOLVED_INDEPENDENTLY -> card.reviews + 1;
            case SOLVED_WITH_HELP -> Math.max(1, card.reviews);
            case COULD_NOT_SOLVE -> 0;
        };
        card.outcome = outcome;
        card.followUp = followUp;
        double minStability = links.stream().mapToDouble(p -> estimates.get(p.getPattern().getId()).getStability()).min().orElse(1);
        int countInterval = card.reviews == 1 ? 3 : card.reviews == 2 ? 7 : card.reviews == 3 ? 14 : 30;
        card.dueDay = day + (outcome == AttemptOutcome.SOLVED_INDEPENDENTLY
                ? (firstReview ? 3 : Math.max(1, Math.min(countInterval, (int) Math.round(-Math.log(0.7) * minStability)))) : 1);
        if (outcome != AttemptOutcome.COULD_NOT_SOLVE) solved.add(item.problem().getId());
        minutesStudied += item.minutes();
        return new Observation(day, item, outcome, followUp);
    }

    private double retention(Memory m, int day) {
        return m.lastDay == null ? 0 : curve.compute(day - m.lastDay, m.stability);
    }
    public double averageRetention(int day) {
        return memory.values().stream().mapToDouble(m -> retention(m, day)).average().orElse(0);
    }
    public double retainedSkill(int day) {
        return memory.values().stream().mapToDouble(m -> m.skill * retention(m, day)).average().orElse(0);
    }
    public double coverage() { return (double) solved.size() / catalog.problems().size(); }
    public int minutesStudied() { return minutesStudied; }
    public double learningRate() { return learningRate; }
    public double initialStability() { return initialStability; }
    public Map<Long, UserPatternState> estimates() { return estimates; }
    public Map<Long, Card> cards() { return cards; }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
