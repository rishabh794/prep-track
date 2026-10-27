package com.preptrack.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.auth.entity.User;
import com.preptrack.config.EngineProperties;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.common.service.TimeService;
import com.preptrack.auth.entity.UserSettings;
import com.preptrack.auth.repository.UserSettingsRepository;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.planner.dto.DailyPlanResponse;
import com.preptrack.planner.entity.DailyPlan;
import com.preptrack.planner.entity.DailyPlanItem;
import com.preptrack.planner.entity.PlanItemType;
import com.preptrack.planner.engine.CandidateCollector;
import com.preptrack.planner.engine.KnapsackSolver;
import com.preptrack.planner.engine.PlanCandidate;
import com.preptrack.planner.engine.ReasonFormatter;
import com.preptrack.planner.repository.DailyPlanItemRepository;
import com.preptrack.planner.repository.DailyPlanRepository;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.repository.RevisionCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannerServiceTest {
    @Spy private EngineProperties policy = new EngineProperties();

    @Mock
    private DailyPlanRepository dailyPlanRepository;

    @Mock
    private DailyPlanItemRepository dailyPlanItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private RevisionCardRepository revisionCardRepository;

    @Mock
    private ProblemPatternRepository problemPatternRepository;

    @Mock
    private CandidateCollector candidateCollector;

    @Mock
    private KnapsackSolver knapsackSolver;

    @Mock
    private ReasonFormatter reasonFormatter;

    @Mock
    private TimeService timeService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PlannerService plannerService;

    private User user;
    private UserSettings settings;

    @BeforeEach
    void setup() {
        lenient().when(timeService.getLocalDate(1L)).thenReturn(LocalDate.now());
        user = User.builder().id(1L).email("user@example.com").build();
        settings = UserSettings.builder()
                .dailyTimeBudgetMin(90)
                .maxNewProblemsDay(3)
                .maxRevisionsDay(10)
                .targetSkillLevel(0.8)
                .build();
    }

    @Test
    @DisplayName("getPlanByDate throws 404 ResourceNotFoundException for missing past/future dates without persisting snapshot")
    void testGetPlanByDateNotFound() {
        LocalDate futureDate = LocalDate.now().plusDays(2);
        when(dailyPlanRepository.findByUserIdAndPlanDateWithItems(1L, futureDate)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> plannerService.getPlanByDate(1L, futureDate))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No plan found for date");

        verify(dailyPlanRepository, never()).save(any());
    }

    @Test
    @DisplayName("regeneratePlan preserves completed and skipped items, deducts quotas, and replaces only uncompleted items")
    void testRegeneratePlanPreservesCompletedAndSkipped() {
        LocalDate today = LocalDate.now();
        Problem p1 = Problem.builder().id(101L).canonicalTitle("Problem 1").build();
        Problem p2 = Problem.builder().id(102L).canonicalTitle("Problem 2").build();
        Problem p3 = Problem.builder().id(103L).canonicalTitle("Problem 3").build();

        DailyPlan existing = DailyPlan.builder()
                .id(50L)
                .user(user)
                .planDate(today)
                .budgetMinutes(90)
                .totalEstimatedMin(75)
                .totalValue(3.0)
                .overdueTotal(0)
                .overdueShown(0)
                .items(new ArrayList<>())
                .build();

        DailyPlanItem item1 = DailyPlanItem.builder()
                .id(1L)
                .dailyPlan(existing)
                .problem(p1)
                .itemType(PlanItemType.NEW_PROBLEM)
                .displayOrder(1)
                .estimatedMinutes(30)
                .valueScore(1.0)
                .completed(true)
                .skipped(false)
                .build();

        DailyPlanItem item2 = DailyPlanItem.builder()
                .id(2L)
                .dailyPlan(existing)
                .problem(p2)
                .itemType(PlanItemType.NEW_PROBLEM)
                .displayOrder(2)
                .estimatedMinutes(20)
                .valueScore(1.0)
                .completed(false)
                .skipped(true)
                .skippedReason("Tired")
                .build();

        DailyPlanItem item3 = DailyPlanItem.builder()
                .id(3L)
                .dailyPlan(existing)
                .problem(p3)
                .itemType(PlanItemType.NEW_PROBLEM)
                .displayOrder(3)
                .estimatedMinutes(25)
                .valueScore(1.0)
                .completed(false)
                .skipped(false)
                .build();

        existing.getItems().addAll(List.of(item1, item2, item3));

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(dailyPlanRepository.findByUserIdAndPlanDateWithItems(1L, today)).thenReturn(Optional.of(existing));
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        Problem p4 = Problem.builder().id(104L).canonicalTitle("Problem 4").build();
        PlanCandidate newCandidate = PlanCandidate.builder()
                .problem(p4)
                .type(PlanItemType.NEW_PROBLEM)
                .timeMinutes(30)
                .value(1.8)
                .isOverdue(false)
                .build();

        // Candidates include p1, p2, p4 (p1 and p2 must be filtered out because they are preserved)
        when(candidateCollector.collectCandidates(eq(1L), eq(0.8), eq(today)))
                .thenReturn(List.of(
                        PlanCandidate.builder().problem(p1).type(PlanItemType.NEW_PROBLEM).timeMinutes(30).value(1.0).build(),
                        PlanCandidate.builder().problem(p2).type(PlanItemType.NEW_PROBLEM).timeMinutes(20).value(1.0).build(),
                        newCandidate
                ));

        // Remaining budget: 90 - 30 = 60 min. Remaining new: 3 - 1 = 2.
        when(knapsackSolver.solve(argThat(list -> list.size() == 1 && list.get(0).getProblem().getId() == 104L), eq(60), eq(10), eq(2)))
                .thenReturn(List.of(newCandidate));

        when(reasonFormatter.format(eq(newCandidate), eq(0.8))).thenReturn("Formatted reason");
        when(dailyPlanRepository.save(any(DailyPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DailyPlanResponse response = plannerService.regeneratePlan(1L, today);

        assertThat(response).isNotNull();
        // Item 3 was replaced by p4. Preserved items: item1 (completed) and item2 (skipped)
        assertThat(existing.getItems()).hasSize(3);
        assertThat(existing.getItems().get(0).getProblem().getId()).isEqualTo(101L);
        assertThat(existing.getItems().get(0).getCompleted()).isTrue();
        assertThat(existing.getItems().get(1).getProblem().getId()).isEqualTo(102L);
        assertThat(existing.getItems().get(1).getSkipped()).isTrue();
        assertThat(existing.getItems().get(2).getProblem().getId()).isEqualTo(104L);
        assertThat(existing.getItems().get(2).getCompleted()).isFalse();
        assertThat(response.getTotalEstimatedMinutes()).isEqualTo(60);
        assertThat(response.getCompletedMinutes()).isEqualTo(30);
        assertThat(response.getRemainingEstimatedMinutes()).isEqualTo(30);
        assertThat(response.getSkippedMinutes()).isEqualTo(20);
        assertThat(response.getSkippedCount()).isEqualTo(1);
    }

    @Test
    void regeneratedBudgetIsUpdatedEvenWhenNoStudySlotsRemain() {
        LocalDate today = LocalDate.now();
        settings.setDailyTimeBudgetMin(60);
        settings.setMaxNewProblemsDay(0);
        settings.setMaxRevisionsDay(0);
        DailyPlan existing = DailyPlan.builder().id(50L).user(user).planDate(today).budgetMinutes(120)
                .items(new ArrayList<>()).build();
        when(dailyPlanRepository.findByUserIdAndPlanDateWithItems(1L, today)).thenReturn(Optional.of(existing));
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(dailyPlanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DailyPlanResponse response = plannerService.regeneratePlan(1L, today);

        assertThat(response.getDailyBudgetMinutes()).isEqualTo(60);
        assertThat(response.getTotalEstimatedMinutes()).isZero();
        verify(candidateCollector, never()).collectCandidates(any(), anyDouble(), any());
    }

    @Test
    void completedPlanItemsCannotBeSkipped() {
        DailyPlanItem item = DailyPlanItem.builder().id(7L).completed(true).skipped(false).build();
        when(dailyPlanItemRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> plannerService.skipPlanItem(1L, 7L, "Done already"))
                .isInstanceOf(com.preptrack.common.exception.BadRequestException.class);
        assertThat(item.getSkipped()).isFalse();
        verify(dailyPlanItemRepository, never()).save(any());
    }
}
