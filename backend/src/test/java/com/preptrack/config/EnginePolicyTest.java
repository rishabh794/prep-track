package com.preptrack.config;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.pattern.entity.*;
import com.preptrack.pattern.repository.*;
import com.preptrack.pattern.service.PatternLearningModel;
import com.preptrack.planner.engine.*;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.entity.*;
import com.preptrack.revision.service.*;
import com.preptrack.simulator.dto.SimulationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EnginePolicyTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(EngineProperties.class);

    @Test
    void defaultsPreserveActiveLearningBehaviorAndBindOverrides() {
        runner.withPropertyValues("preptrack.engine.with-help-skill-gain=0.2", "preptrack.engine.recall-minutes=7",
                "preptrack.engine.default-budget-minutes=60", "preptrack.engine.revision-intervals=2,5,10")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var policy = context.getBean(EngineProperties.class);
                    assertThat(policy.getWithHelpSkillGain()).isEqualTo(0.2);
                    assertThat(policy.getRecallMinutes()).isEqualTo(7);
                    assertThat(policy.defaultSettings(null).getDailyTimeBudgetMin()).isEqualTo(60);
                    assertThat(policy.revisionIntervalDays(20)).isEqualTo(10);
                });
        var defaults = new EngineProperties();
        assertThat(defaults.getWithHelpSkillGain()).isEqualTo(0.03);
        assertThat(defaults.getCouldNotSolveSkillDecay()).isEqualTo(0.02);
        assertThat(defaults.getWithHelpStabilityMultiplier()).isEqualTo(1);
    }

    @Test
    void refusesInvalidPolicyAtStartup() {
        for (String invalid : List.of("preptrack.engine.maintenance-floor=-0.1", "preptrack.engine.recall-minutes=0",
                "preptrack.engine.revision-retention-target=1", "preptrack.engine.revision-intervals=7,3",
                "preptrack.engine.max-stability=0.5", "preptrack.engine.max-interview-boost=0.1")) {
            runner.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void configuredLearningValuesChangeActualUpdatesAndRecallPreservesAcquisition() {
        var policy = new EngineProperties();
        policy.setIndependentSolveSkillGain(0.4);
        policy.setWithHelpSkillGain(0.2);
        policy.setCouldNotSolveSkillDecay(0.1);
        policy.setWithHelpStabilityMultiplier(1.2);
        policy.setIndependentSolveStabilityMultiplier(2);
        var state = UserPatternState.builder().skill(0.5).stability(2.0).build();
        var now = LocalDateTime.of(2026, 1, 1, 12, 0);
        PatternLearningModel.apply(state, AttemptOutcome.SOLVED_WITH_HELP, 0.5, false, now, policy);
        assertThat(state.getSkill()).isCloseTo(0.6, within(1e-10));
        assertThat(state.getStability()).isCloseTo(2.4, within(1e-10));
        PatternLearningModel.apply(state, AttemptOutcome.COULD_NOT_SOLVE, 0.5, false, now, policy);
        assertThat(state.getSkill()).isCloseTo(0.55, within(1e-10));
        PatternLearningModel.apply(state, AttemptOutcome.SOLVED_INDEPENDENTLY, 0.5, true, now, policy);
        assertThat(state.getSkill()).isCloseTo(0.55, within(1e-10));
        assertThat(state.getStability()).isCloseTo(2.4, within(1e-10));
    }

    @Test
    void configuredScoresAndTimingAffectPlans() {
        var policy = new EngineProperties();
        policy.setMaintenanceFloor(0.4);
        policy.setRetryBoostMultiplier(3);
        policy.setCodeFromScratchMultiplier(2);
        policy.setDiversityMultiplier(0.25);
        policy.setRecallMinutes(7);
        policy.setEdgeCaseMinimumMinutes(12);
        policy.setEdgeCaseTimeFraction(0.75);
        var relevance = mock(InterviewRelevanceCalculator.class);
        when(relevance.calculateWithInterviews(any(), any(), any())).thenReturn(
                InterviewRelevanceCalculator.InterviewContext.builder().relevanceMultiplier(1.0).build());
        var pattern = Pattern.builder().id(1L).name("Pattern").build();
        var problem = Problem.builder().id(1L).estimatedMinutes(20).build();
        var links = List.of(ProblemPattern.builder().problem(problem).pattern(pattern).weight(1.0).build());
        var state = UserPatternState.builder().pattern(pattern).skill(0.95).build();
        var maintenance = new MaintenanceScorer(mock(ProblemPatternRepository.class), mock(UserPatternStateRepository.class),
                new RetentionCalculator(), relevance, policy);
        var score = maintenance.score(1L, problem, 0.8, LocalDate.of(2026, 1, 1), true, FollowUpType.CODE_FROM_SCRATCH,
                links, Map.of(1L, state), List.of());
        assertThat(score.value).isEqualTo(2.4);
        var acquisition = new AcquisitionScorer(mock(ProblemPatternRepository.class), mock(UserPatternStateRepository.class), relevance, policy);
        state.setSkill(0.2);
        assertThat(acquisition.score(1L, problem, 0.8, LocalDate.of(2026, 1, 1), Set.of(1L),
                links, Map.of(1L, state), List.of()).value).isEqualTo(0.15);
        var times = new RevisionTimeEstimator(policy);
        assertThat(times.estimateMinutes(RevisionCard.builder().problem(problem).build())).isEqualTo(7);
        assertThat(times.estimateMinutes(RevisionCard.builder().problem(problem).requiredFollowUp(FollowUpType.EDGE_CASE_LIST).build())).isEqualTo(15);
    }

    @Test
    void loweringTheConfiguredStabilityMaximumAlsoBoundsExistingStatesAfterFailure() {
        var policy = new EngineProperties();
        policy.setMaxStability(10);
        var state = UserPatternState.builder().skill(0.5).stability(100.0).build();
        PatternLearningModel.apply(state, AttemptOutcome.COULD_NOT_SOLVE, 1, false, LocalDateTime.now(), policy);
        assertThat(state.getStability()).isEqualTo(10);
    }

    @Test
    void configuredSchedulingUsesIntervalsAndRetentionThreshold() {
        var policy = new EngineProperties();
        policy.setRevisionIntervals(List.of(8, 12));
        policy.setRevisionRetentionTarget(0.8);
        policy.setRevisionRetryDays(2);
        var schedule = new RevisionSchedulingPolicy(policy);
        assertThat(schedule.intervalDays(AttemptOutcome.SOLVED_INDEPENDENTLY, 1, true, 10)).isEqualTo(8);
        assertThat(schedule.intervalDays(AttemptOutcome.SOLVED_INDEPENDENTLY, 5, false, 10)).isEqualTo(2);
        assertThat(schedule.intervalDays(AttemptOutcome.COULD_NOT_SOLVE, 0, false, 10)).isEqualTo(2);
    }

    @Test
    void savedReportsSupportLegacyAndExplicitPolicySnapshots() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        String legacy = """
                {"runId":"fd6e1dc5-88cd-46cf-835e-6e98b7f77189","createdAt":"2026-01-01T00:00:00Z",
                 "modelVersion":"1.0","config":{},"sheetName":"Example","problemCount":1,"patternCount":1,
                 "results":[],"comparisons":[],"caveats":[],"textSummary":"Legacy report"}
                """;
        assertThat(mapper.readValue(legacy, SimulationResult.class).enginePolicy()).isNull();
        var policy = new EngineProperties();
        policy.setRecallMinutes(8);
        ObjectNode json = (ObjectNode) mapper.readTree(legacy);
        json.set("enginePolicy", mapper.valueToTree(policy.snapshot()));
        var restored = mapper.treeToValue(json, SimulationResult.class);
        assertThat(restored.enginePolicy().getRecallMinutes()).isEqualTo(8);
        assertThat(restored.enginePolicy().getWithHelpSkillGain()).isEqualTo(0.03);
    }
}
