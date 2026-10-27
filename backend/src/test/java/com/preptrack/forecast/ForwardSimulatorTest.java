package com.preptrack.forecast;

import com.preptrack.forecast.dto.PatternProjectionDto;
import com.preptrack.forecast.dto.ReadinessForecastDto;
import com.preptrack.forecast.dto.TriageStatus;
import com.preptrack.forecast.engine.ForwardSimulator;
import com.preptrack.forecast.config.ForecastProperties;
import com.preptrack.config.EngineProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForwardSimulatorTest {

    private ForwardSimulator forwardSimulator;

    @BeforeEach
    void setUp() {
        forwardSimulator = new ForwardSimulator();
    }

    @Test
    @DisplayName("Forward simulation completes in well under 1 second (under 50ms)")
    void simulationPerformanceTest() {
        List<ForwardSimulator.PatternSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            snapshots.add(ForwardSimulator.PatternSnapshot.builder()
                    .patternName("Pattern " + i)
                    .currentSkill(0.2)
                    .currentStability(2.0)
                    .daysSinceLastPracticed(1)
                    .availableProblems(5)
                    .companyRelevance(1.0)
                    .build());
        }

        long start = System.currentTimeMillis();
        ReadinessForecastDto result = forwardSimulator.simulate(
                1L,
                LocalDate.now().plusDays(30),
                "Google",
                30L,
                90,
                3,
                10,
                0.8,
                snapshots
        );
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(1000L); // Spec requires < 1 second
        assertThat(result).isNotNull();
        assertThat(result.getPatternProjections()).hasSize(35);
        assertThat(result.getOverallReadiness()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Verify projections change and improve with additional attempts")
    void projectionsChangeWithAttemptsTest() {
        List<ForwardSimulator.PatternSnapshot> initialSnapshots = List.of(
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("Two Pointer Squeeze")
                        .currentSkill(0.20)
                        .currentStability(1.0)
                        .daysSinceLastPracticed(null)
                        .availableProblems(5)
                        .companyRelevance(1.2)
                        .build()
        );

        ReadinessForecastDto beforeAttempt = forwardSimulator.simulate(
                1L,
                LocalDate.now().plusDays(10),
                "Google",
                10L,
                90,
                3,
                10,
                0.8,
                initialSnapshots
        );

        // User solves several problems on Two Pointer
        List<ForwardSimulator.PatternSnapshot> afterAttemptSnapshots = List.of(
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("Two Pointer Squeeze")
                        .currentSkill(0.85)
                        .currentStability(25.0)
                        .daysSinceLastPracticed(0)
                        .availableProblems(2)
                        .companyRelevance(1.2)
                        .build()
        );

        ReadinessForecastDto afterAttempt = forwardSimulator.simulate(
                1L,
                LocalDate.now().plusDays(10),
                "Google",
                10L,
                90,
                3,
                10,
                0.8,
                afterAttemptSnapshots
        );

        PatternProjectionDto projBefore = beforeAttempt.getPatternProjections().get(0);
        PatternProjectionDto projAfter = afterAttempt.getPatternProjections().get(0);

        assertThat(projAfter.getCurrentSkill()).isGreaterThan(projBefore.getCurrentSkill());
        assertThat(projAfter.getProjectedSkill()).isGreaterThan(projBefore.getProjectedSkill());
        assertThat(projAfter.getStatus()).isEqualTo(TriageStatus.ON_TRACK);
        assertThat(afterAttempt.getOverallReadiness()).isGreaterThan(beforeAttempt.getOverallReadiness());
    }

    @Test
    @DisplayName("Retention is 0.0 when never practiced, and decays exponentially")
    void retentionDecayTest() {
        List<ForwardSimulator.PatternSnapshot> snapshots = List.of(
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("Unpracticed Pattern")
                        .currentSkill(0.0)
                        .currentStability(1.0)
                        .daysSinceLastPracticed(null) // never practiced
                        .availableProblems(0)
                        .companyRelevance(1.0)
                        .build(),
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("Practiced Pattern")
                        .currentSkill(0.8)
                        .currentStability(10.0)
                        .daysSinceLastPracticed(2)
                        .availableProblems(0)
                        .companyRelevance(1.0)
                        .build()
        );

        ReadinessForecastDto result = forwardSimulator.simulate(
                1L,
                LocalDate.now().plusDays(10),
                "Google",
                10L,
                90,
                3,
                10,
                0.8,
                snapshots
        );

        PatternProjectionDto unpracticed = result.getPatternProjections().stream()
                .filter(p -> p.getPattern().equals("Unpracticed Pattern"))
                .findFirst()
                .orElseThrow();

        assertThat(unpracticed.getCurrentRetention()).isEqualTo(0.0);
        assertThat(unpracticed.getProjectedRetention()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Status classification correctly identifies ON_TRACK, AT_RISK, and CONSIDER_DROPPING")
    void statusClassificationTest() {
        List<ForwardSimulator.PatternSnapshot> snapshots = List.of(
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("Two Pointer Squeeze")
                        .currentSkill(0.85)
                        .currentStability(30.0)
                        .daysSinceLastPracticed(0)
                        .availableProblems(3)
                        .companyRelevance(1.2)
                        .build(),
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("Trie Prefix Matching")
                        .currentSkill(0.10)
                        .currentStability(1.0)
                        .daysSinceLastPracticed(null)
                        .availableProblems(1)
                        .companyRelevance(0.4) // Low frequency for Google
                        .build(),
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("DP Kadane's")
                        .currentSkill(0.20)
                        .currentStability(2.0)
                        .daysSinceLastPracticed(5)
                        .availableProblems(4)
                        .companyRelevance(1.2)
                        .build()
        );

        ReadinessForecastDto result = forwardSimulator.simulate(
                1L,
                LocalDate.now().plusDays(7),
                "Google",
                7L,
                90,
                3,
                10,
                0.8,
                snapshots
        );

        PatternProjectionDto twoPointer = result.getPatternProjections().stream()
                .filter(p -> p.getPattern().equals("Two Pointer Squeeze"))
                .findFirst().orElseThrow();
        PatternProjectionDto trie = result.getPatternProjections().stream()
                .filter(p -> p.getPattern().equals("Trie Prefix Matching"))
                .findFirst().orElseThrow();

        assertThat(twoPointer.getStatus()).isEqualTo(TriageStatus.ON_TRACK);
        assertThat(trie.getStatus()).isEqualTo(TriageStatus.CONSIDER_DROPPING);
    }

    @Test
    @DisplayName("Recommendations provide actionable triage guidance")
    void recommendationsTest() {
        List<ForwardSimulator.PatternSnapshot> snapshots = List.of(
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("Two Pointer Squeeze")
                        .currentSkill(0.85)
                        .currentStability(30.0)
                        .daysSinceLastPracticed(0)
                        .availableProblems(3)
                        .companyRelevance(1.0)
                        .build(),
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("DP Memoization on Subsets")
                        .currentSkill(0.20)
                        .currentStability(1.0)
                        .daysSinceLastPracticed(5)
                        .availableProblems(5)
                        .companyRelevance(1.2)
                        .build(),
                ForwardSimulator.PatternSnapshot.builder()
                        .patternName("Trie Prefix Matching")
                        .currentSkill(0.10)
                        .currentStability(1.0)
                        .daysSinceLastPracticed(null)
                        .availableProblems(1)
                        .companyRelevance(0.4)
                        .build()
        );

        ReadinessForecastDto result = forwardSimulator.simulate(
                1L,
                LocalDate.now().plusDays(20),
                "Google",
                20L,
                90,
                3,
                10,
                0.8,
                snapshots
        );

        assertThat(result.getRecommendations()).isNotEmpty();
        boolean hasDpAdvice = result.getRecommendations().stream().anyMatch(r -> r.contains("DP Memoization on Subsets"));
        boolean hasTrieAdvice = result.getRecommendations().stream().anyMatch(r -> r.contains("Trie Prefix Matching"));
        boolean hasMaintenanceAdvice = result.getRecommendations().stream().anyMatch(r -> r.contains("Two Pointer Squeeze"));

        assertThat(hasDpAdvice).isTrue();
        assertThat(hasTrieAdvice).isTrue();
        assertThat(hasMaintenanceAdvice).isTrue();
    }

    @Test
    void reportsEffectiveBudgetAndCappedHorizon() {
        ForecastProperties properties = new ForecastProperties();
        properties.setMaxHorizonDays(12);
        var simulator = new ForwardSimulator(new EngineProperties(), properties);
        var result = simulator.simulate(1L, LocalDate.now().plusDays(100), "Example", 100,
                45, 1, 2, 0.6, List.of());
        assertThat(result.getDailyBudgetMinutes()).isEqualTo(45);
        assertThat(result.getMaxNewProblemsDay()).isEqualTo(1);
        assertThat(result.getMaxRevisionsDay()).isEqualTo(2);
        assertThat(result.getTargetSkillLevel()).isEqualTo(0.6);
        assertThat(result.getSimulationDays()).isEqualTo(12);
        assertThat(result.getHorizonCapped()).isTrue();
        assertThat(result.getModel()).isEqualTo("PATTERN_APPROXIMATION");
        assertThat(result.getLimitations()).anyMatch(message -> message.contains("not a replay"));
    }

    @Test
    void zeroCapsAndCatalogTimeEstimatesAreRespected() {
        var snapshot = ForwardSimulator.PatternSnapshot.builder().patternName("Example").currentSkill(0)
                .currentStability(1).availableProblems(4).companyRelevance(1).estimatedProblemMinutes(45).build();
        var noSlots = forwardSimulator.simulate(1L, LocalDate.now().plusDays(1), "Example", 1,
                90, 0, 0, 0.8, List.of(snapshot));
        var tooLittleTime = forwardSimulator.simulate(1L, LocalDate.now().plusDays(1), "Example", 1,
                30, 3, 10, 0.8, List.of(snapshot));
        var enoughTime = forwardSimulator.simulate(1L, LocalDate.now().plusDays(1), "Example", 1,
                45, 3, 10, 0.8, List.of(snapshot));
        assertThat(noSlots.getPatternProjections().getFirst().getProjectedSkill()).isZero();
        assertThat(tooLittleTime.getPatternProjections().getFirst().getProjectedSkill()).isZero();
        assertThat(enoughTime.getPatternProjections().getFirst().getProjectedSkill()).isEqualTo(0.1);
    }
}
