package com.preptrack.planner.engine;

import com.preptrack.pattern.entity.Pattern;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.entity.UserPatternState;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.pattern.repository.UserPatternStateRepository;
import com.preptrack.problem.entity.Problem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringEdgeCasesTest {

    @Mock
    private ProblemPatternRepository problemPatternRepository;

    @Mock
    private UserPatternStateRepository userPatternStateRepository;

    @Mock
    private RetentionCalculator retentionCalculator;

    @Mock
    private InterviewRelevanceCalculator interviewRelevanceCalculator;

    @InjectMocks
    private AcquisitionScorer acquisitionScorer;

    @InjectMocks
    private MaintenanceScorer maintenanceScorer;

    private Problem testProblem;
    private Pattern testPattern;

    @BeforeEach
    void setup() {
        testProblem = Problem.builder().id(101L).canonicalTitle("Two Sum").build();
        testPattern = Pattern.builder().id(1L).name("Two Pointers").build();
    }

    @Test
    @DisplayName("Acquisition value clamps at 0.0 for mastered patterns (never negative)")
    void testAcquisitionValueClampsAtZero() {
        ProblemPattern pp = ProblemPattern.builder().problem(testProblem).pattern(testPattern).weight(1.0).build();
        when(problemPatternRepository.findByProblemIdWithPattern(101L)).thenReturn(List.of(pp));

        // User skill is 0.95, target is 0.80
        UserPatternState state = UserPatternState.builder().pattern(testPattern).skill(0.95).build();
        when(userPatternStateRepository.findByUserIdAndPatternIdIn(eq(1L), any())).thenReturn(List.of(state));
        when(interviewRelevanceCalculator.calculate(any(), any(), any())).thenReturn(
                InterviewRelevanceCalculator.InterviewContext.builder().relevanceMultiplier(1.0).build()
        );

        AcquisitionScorer.ScoredResult result = acquisitionScorer.score(1L, testProblem, 0.80, LocalDate.now(), Set.of());
        assertThat(result.value).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Maintenance value retains floor of 0.15 for mastered patterns")
    void testMaintenanceValueHasFloor() {
        ProblemPattern pp = ProblemPattern.builder().problem(testProblem).pattern(testPattern).weight(1.0).build();
        when(problemPatternRepository.findByProblemIdWithPattern(101L)).thenReturn(List.of(pp));

        // User skill is 0.95, target is 0.80 -> target - skill = -0.15, but floor gives 0.15
        UserPatternState state = UserPatternState.builder()
                .pattern(testPattern)
                .skill(0.95)
                .stability(5.0)
                .lastPracticedAt(LocalDateTime.now().minusDays(10))
                .build();
        when(userPatternStateRepository.findByUserIdAndPatternIdIn(eq(1L), any())).thenReturn(List.of(state));
        when(retentionCalculator.compute(any(), eq(5.0), any())).thenReturn(0.25);
        when(interviewRelevanceCalculator.calculate(any(), any(), any())).thenReturn(
                InterviewRelevanceCalculator.InterviewContext.builder().relevanceMultiplier(1.0).build()
        );

        MaintenanceScorer.ScoredResult result = maintenanceScorer.score(1L, testProblem, 0.80, LocalDate.now(), false, null);

        // value = 1.0 * max(0.15, 0.80 - 0.95) * (1 - 0.25) = 0.15 * 0.75 = 0.1125 -> ~0.11
        assertThat(result.value).isGreaterThan(0.0);
        assertThat(result.value).isEqualTo(0.11);
    }

    @Test
    @DisplayName("Diversity penalty applies 0.5x multiplier for same primary pattern")
    void testDiversityPenaltyApplies() {
        ProblemPattern pp = ProblemPattern.builder().problem(testProblem).pattern(testPattern).weight(1.0).build();
        when(problemPatternRepository.findByProblemIdWithPattern(101L)).thenReturn(List.of(pp));

        UserPatternState state = UserPatternState.builder().pattern(testPattern).skill(0.2).build();
        when(userPatternStateRepository.findByUserIdAndPatternIdIn(eq(1L), any())).thenReturn(List.of(state));
        when(interviewRelevanceCalculator.calculate(any(), any(), any())).thenReturn(
                InterviewRelevanceCalculator.InterviewContext.builder().relevanceMultiplier(1.0).build()
        );

        // Without diversity penalty
        AcquisitionScorer.ScoredResult withoutPenalty = acquisitionScorer.score(1L, testProblem, 0.80, LocalDate.now(), Set.of());
        // With diversity penalty (pattern 1L is already in plan)
        AcquisitionScorer.ScoredResult withPenalty = acquisitionScorer.score(1L, testProblem, 0.80, LocalDate.now(), Set.of(1L));

        assertThat(withPenalty.value).isEqualTo(Math.round(withoutPenalty.value * 0.5 * 100.0) / 100.0);
    }
}
