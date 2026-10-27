package com.preptrack.simulator;

import com.preptrack.application.repository.InterviewRepository;
import com.preptrack.company.service.CompanyProfileService;
import com.preptrack.config.EngineProperties;
import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.pattern.entity.*;
import com.preptrack.pattern.repository.*;
import com.preptrack.pattern.service.PatternLearningModel;
import com.preptrack.planner.engine.*;
import com.preptrack.problem.entity.*;
import com.preptrack.revision.service.RevisionTimeEstimator;
import com.preptrack.simulator.dto.SimulationConfig;
import com.preptrack.simulator.engine.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SimulationEngineTest {
    private final EngineProperties policy = new EngineProperties();
    private final InterviewRelevanceCalculator relevance = new InterviewRelevanceCalculator(mock(InterviewRepository.class), mock(CompanyProfileService.class), policy, mock(com.preptrack.common.service.TimeService.class));
    private final PrepTrackStrategy strategy = new PrepTrackStrategy(
            new AcquisitionScorer(mock(ProblemPatternRepository.class), mock(UserPatternStateRepository.class), relevance, policy),
            new MaintenanceScorer(mock(ProblemPatternRepository.class), mock(UserPatternStateRepository.class), new RetentionCalculator(), relevance, policy, mock(com.preptrack.common.service.TimeService.class)),
            new KnapsackSolver(), new RevisionTimeEstimator(policy));

    private SimulationCatalog catalog() {
        var patterns = new ArrayList<Pattern>();
        var problems = new ArrayList<Problem>();
        Map<Long, List<ProblemPattern>> links = new LinkedHashMap<>();
        for (long i = 1; i <= 4; i++) {
            var pattern = Pattern.builder().id(i).name("Pattern " + i).build();
            patterns.add(pattern);
            for (long j = 0; j < 3; j++) {
                var problem = Problem.builder().id(i * 10 + j).canonicalTitle("Problem " + i + j)
                        .difficulty(j == 0 ? Difficulty.EASY : Difficulty.MEDIUM).estimatedMinutes(j == 0 ? 15 : 30).build();
                problems.add(problem);
                links.put(problem.getId(), List.of(ProblemPattern.builder().problem(problem).pattern(pattern).weight(1.0).build()));
            }
        }
        return new SimulationCatalog("Fixture", problems, patterns, links);
    }

    @Test
    void curvesAreBoundedDecayAndMismatch() {
        ForgettingCurve exponential = new ExponentialForgetting();
        ForgettingCurve power = new PowerLawForgetting();
        assertThat(exponential.compute(0, 1)).isEqualTo(1);
        assertThat(power.compute(0, 1)).isEqualTo(1);
        assertThat(exponential.compute(10, 2)).isBetween(0.0, exponential.compute(1, 2));
        assertThat(power.compute(10, 2)).isBetween(exponential.compute(10, 2), power.compute(1, 2));
        assertThat(exponential.compute(2, 0)).isFinite();
    }

    @Test
    void sampledLearnersHavePairedParametersAndIndependentHiddenStates() {
        var catalog = catalog();
        var a = new SyntheticLearner(catalog, new ExponentialForgetting(), 42);
        var b = new SyntheticLearner(catalog, new PowerLawForgetting(), 42);
        var other = new SyntheticLearner(catalog, new ExponentialForgetting(), 43);
        assertThat(a.learningRate()).isEqualTo(b.learningRate()).isNotEqualTo(other.learningRate()).isPositive();
        assertThat(a.initialStability()).isEqualTo(b.initialStability()).isNotEqualTo(other.initialStability()).isPositive();
        assertThat(a.averageRetention(1)).isZero();
        a.study(new SyntheticLearner.StudyItem(catalog.problems().getFirst(), false, false, 15), 1);
        double truth = a.retainedSkill(2);
        a.estimates().values().forEach(s -> { s.setSkill(1.0); s.setStability(365.0); });
        assertThat(a.retainedSkill(2)).isEqualTo(truth);
        assertThat(b.averageRetention(2)).isZero();
        assertThat(a.averageRetention(2)).isGreaterThan(0).isLessThan(0.25); // untouched patterns remain zero
    }

    @Test
    void runsAllEightCombinationsReproduciblyWithoutForcingAWinner() {
        var engine = new SimulationEngine(strategy, policy);
        var config = new SimulationConfig(12, 5, 60, 2, 4, 0.8, 42L, null);
        var result = engine.run(config, catalog());
        var repeat = engine.run(config, catalog());
        assertThat(result.results()).hasSize(8).isEqualTo(repeat.results());
        assertThat(result.comparisons()).hasSize(6).isEqualTo(repeat.comparisons());
        assertThat(result.enginePolicy()).isNotSameAs(policy);
        assertThat(result.enginePolicy().getRecallMinutes()).isEqualTo(policy.getRecallMinutes());
        result.results().forEach(row -> {
            assertThat(row.retention().mean()).isBetween(0.0, 1.0);
            assertThat(row.retainedSkill().mean()).isBetween(0.0, row.retention().mean());
            assertThat(row.coverage().mean()).isBetween(0.0, 1.0);
            assertThat(row.minutesStudied().mean()).isLessThanOrEqualTo(12 * 60);
            assertThat(row.dailySnapshots()).hasSize(12);
        });
        result.comparisons().forEach(c -> assertThat(c.prepTrackHigher()).isEqualTo(c.retentionDifference() > 0));
        assertThat(result.textSummary()).contains("Caveat:", "POWER_LAW", "seed 42");
        policy.setRecallMinutes(9);
        assertThat(result.enginePolicy().getRecallMinutes()).isEqualTo(5);
    }

    @Test
    void baselinesUseFixedEaseAndSevenDayReviews() {
        var catalog = catalog();
        var learner = new SyntheticLearner(catalog, new ExponentialForgetting(), 42);
        var item = new SyntheticLearner.StudyItem(catalog.problems().getFirst(), false, false, 15);
        var first = learner.study(item, 1);
        var random = new BaselinePlanner(BaselinePlanner.Kind.RANDOM, 42);
        random.observe(learner, first);
        assertThat(learner.cards().get(item.problem().getId()).dueDay).isEqualTo(8);
        var sm2 = new BaselinePlanner(BaselinePlanner.Kind.PLAIN_SM2, 42);
        sm2.observe(learner, new SyntheticLearner.Observation(1, item, AttemptOutcome.SOLVED_INDEPENDENTLY, null));
        assertThat(learner.cards().get(item.problem().getId()).dueDay).isEqualTo(2);
        sm2.observe(learner, new SyntheticLearner.Observation(2, item, AttemptOutcome.SOLVED_INDEPENDENTLY, null));
        assertThat(learner.cards().get(item.problem().getId()).dueDay).isEqualTo(8);
        sm2.observe(learner, new SyntheticLearner.Observation(8, item, AttemptOutcome.SOLVED_INDEPENDENTLY, null));
        assertThat(learner.cards().get(item.problem().getId()).dueDay).isEqualTo(23);
        sm2.observe(learner, new SyntheticLearner.Observation(23, item, AttemptOutcome.COULD_NOT_SOLVE, null));
        assertThat(learner.cards().get(item.problem().getId()).dueDay).isEqualTo(24);
    }

    @Test
    void sharedObservationModelPreservesRecallAndAttemptRules() {
        var state = UserPatternState.builder().skill(0.5).stability(2.0).build();
        var now = LocalDateTime.of(2026, 1, 1, 12, 0);
        PatternLearningModel.apply(state, AttemptOutcome.SOLVED_INDEPENDENTLY, 0.5, true, now);
        assertThat(state.getSkill()).isEqualTo(0.5);
        assertThat(state.getStability()).isEqualTo(3);
        PatternLearningModel.apply(state, AttemptOutcome.SOLVED_WITH_HELP, 0.5, false, now);
        assertThat(state.getSkill()).isCloseTo(0.515, within(0.00001));
        PatternLearningModel.apply(state, AttemptOutcome.COULD_NOT_SOLVE, 0.5, false, now);
        assertThat(state.getSkill()).isCloseTo(0.505, within(0.00001));
        assertThat(state.getTotalAttempts()).isEqualTo(3);
    }

    @Test
    void computesSampleDeviationIncludingSingleLearner() {
        assertThat(SimulationEngine.metric(new double[]{0.2, 0.4, 0.6}).mean()).isCloseTo(0.4, within(0.00001));
        assertThat(SimulationEngine.metric(new double[]{0.2, 0.4, 0.6}).stdDev()).isCloseTo(0.2, within(0.00001));
        assertThat(SimulationEngine.metric(new double[]{0.2}).stdDev()).isZero();
    }
}
