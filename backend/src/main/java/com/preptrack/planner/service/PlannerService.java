package com.preptrack.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.preptrack.auth.entity.User;
import com.preptrack.auth.entity.UserSettings;
import com.preptrack.auth.repository.UserRepository;
import com.preptrack.auth.repository.UserSettingsRepository;
import com.preptrack.common.exception.ResourceNotFoundException;
import com.preptrack.pattern.entity.ProblemPattern;
import com.preptrack.pattern.repository.ProblemPatternRepository;
import com.preptrack.planner.dto.*;
import com.preptrack.planner.engine.CandidateCollector;
import com.preptrack.planner.engine.KnapsackSolver;
import com.preptrack.planner.engine.PlanCandidate;
import com.preptrack.planner.engine.ReasonFormatter;
import com.preptrack.planner.entity.DailyPlan;
import com.preptrack.planner.entity.DailyPlanItem;
import com.preptrack.planner.entity.PlanItemType;
import com.preptrack.planner.repository.DailyPlanItemRepository;
import com.preptrack.planner.repository.DailyPlanRepository;
import com.preptrack.revision.repository.RevisionCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerService {

    private final DailyPlanRepository dailyPlanRepository;
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;
    private final RevisionCardRepository revisionCardRepository;
    private final ProblemPatternRepository problemPatternRepository;
    private final CandidateCollector candidateCollector;
    private final KnapsackSolver knapsackSolver;
    private final ReasonFormatter reasonFormatter;
    private final ObjectMapper objectMapper;

    @Transactional
    public DailyPlanResponse getOrCreatePlanForToday(Long userId, LocalDate date) {
        LocalDate planDate = date != null ? date : LocalDate.now();

        Optional<DailyPlan> existingPlan = dailyPlanRepository.findByUserIdAndPlanDateWithItems(userId, planDate);
        if (existingPlan.isPresent()) {
            return mapToResponse(existingPlan.get());
        }

        return generateAndPersistPlan(userId, planDate);
    }

    @Transactional
    public DailyPlanResponse regeneratePlan(Long userId, LocalDate date) {
        LocalDate planDate = date != null ? date : LocalDate.now();
        dailyPlanRepository.findByUserIdAndPlanDate(userId, planDate)
                .ifPresent(existing -> {
                    dailyPlanRepository.delete(existing);
                    dailyPlanRepository.flush();
                });
        return generateAndPersistPlan(userId, planDate);
    }

    @Transactional
    public DailyPlanResponse skipPlanItem(Long userId, Long itemId, String reason) {
        DailyPlanItem item = dailyPlanItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan item not found: " + itemId));

        item.setSkipped(true);
        item.setSkippedReason(reason);
        dailyPlanItemRepository.save(item);

        return mapToResponse(item.getDailyPlan());
    }

    private DailyPlanResponse generateAndPersistPlan(Long userId, LocalDate planDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElse(UserSettings.builder()
                        .dailyTimeBudgetMin(90)
                        .maxNewProblemsDay(3)
                        .maxRevisionsDay(10)
                        .targetSkillLevel(0.8)
                        .build());

        int budget = settings.getDailyTimeBudgetMin() != null ? settings.getDailyTimeBudgetMin() : 90;
        int maxReview = settings.getMaxRevisionsDay() != null ? settings.getMaxRevisionsDay() : 10;
        int maxNew = settings.getMaxNewProblemsDay() != null ? settings.getMaxNewProblemsDay() : 3;
        double targetSkill = settings.getTargetSkillLevel() != null ? settings.getTargetSkillLevel() : 0.8;

        // 1. Collect candidates
        List<PlanCandidate> candidates = candidateCollector.collectCandidates(userId, targetSkill, planDate);

        // 2. Solve 3D knapsack
        List<PlanCandidate> selected = knapsackSolver.solve(candidates, budget, maxReview, maxNew);

        // 3. Count overdue cards
        int overdueTotal = revisionCardRepository.countOverdueCards(userId, planDate);
        int overdueShown = (int) selected.stream()
                .filter(c -> c.getType() == PlanItemType.REVISION || c.getType() == PlanItemType.RETRY)
                .count();

        // 4. Build and persist DailyPlan
        int totalMinutes = selected.stream().mapToInt(PlanCandidate::getTimeMinutes).sum();
        double totalValue = selected.stream().mapToDouble(PlanCandidate::getValue).sum();

        DailyPlan dailyPlan = DailyPlan.builder()
                .user(user)
                .planDate(planDate)
                .budgetMinutes(budget)
                .totalEstimatedMin(totalMinutes)
                .totalValue(Math.round(totalValue * 100.0) / 100.0)
                .overdueTotal(overdueTotal)
                .overdueShown(overdueShown)
                .build();

        DailyPlan savedPlan = dailyPlanRepository.save(dailyPlan);

        List<DailyPlanItem> items = new ArrayList<>();
        int order = 1;
        for (PlanCandidate c : selected) {
            String reason = reasonFormatter.format(c, targetSkill);
            String jsonStr = null;
            try {
                jsonStr = objectMapper.writeValueAsString(c.getValueBreakdown());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize ValueBreakdown for problem {}", c.getProblem().getId(), e);
            }

            DailyPlanItem item = DailyPlanItem.builder()
                    .dailyPlan(savedPlan)
                    .problem(c.getProblem())
                    .itemType(c.getType())
                    .displayOrder(order++)
                    .estimatedMinutes(c.getTimeMinutes())
                    .valueScore(c.getValue())
                    .valueJson(jsonStr)
                    .reasonSummary(reason)
                    .followUpType(c.getFollowUpType())
                    .followUpDetail(c.getFollowUpDetail())
                    .completed(false)
                    .skipped(false)
                    .build();
            items.add(item);
        }

        savedPlan.setItems(items);
        DailyPlan fullySaved = dailyPlanRepository.save(savedPlan);

        return mapToResponse(fullySaved);
    }

    private DailyPlanResponse mapToResponse(DailyPlan plan) {
        List<DailyPlanItem> items = plan.getItems() != null ? plan.getItems() : Collections.emptyList();

        int completedMinutes = 0;
        int completedCount = 0;

        List<Long> problemIds = items.stream().map(i -> i.getProblem().getId()).toList();
        Map<Long, List<String>> problemPatternsMap = new HashMap<>();
        if (!problemIds.isEmpty()) {
            List<ProblemPattern> pps = problemPatternRepository.findByProblemIdInWithPattern(problemIds);
            for (ProblemPattern pp : pps) {
                problemPatternsMap.computeIfAbsent(pp.getProblem().getId(), k -> new ArrayList<>())
                        .add(pp.getPattern().getName());
            }
        }

        Map<PlanItemType, List<PlanItemDto>> sectionMap = new LinkedHashMap<>();
        sectionMap.put(PlanItemType.RETRY, new ArrayList<>());
        sectionMap.put(PlanItemType.REVISION, new ArrayList<>());
        sectionMap.put(PlanItemType.NEW_PROBLEM, new ArrayList<>());

        for (DailyPlanItem item : items) {
            if (Boolean.TRUE.equals(item.getCompleted())) {
                completedMinutes += item.getEstimatedMinutes();
                completedCount++;
            }

            ValueBreakdown breakdown = null;
            if (item.getValueJson() != null) {
                try {
                    breakdown = objectMapper.readValue(item.getValueJson(), ValueBreakdown.class);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize ValueBreakdown for item {}", item.getId(), e);
                }
            }

            List<String> patternNames = problemPatternsMap.getOrDefault(item.getProblem().getId(), Collections.emptyList());

            PlanItemDto dto = PlanItemDto.builder()
                    .planItemId(item.getId())
                    .problemId(item.getProblem().getId())
                    .title(item.getProblem().getCanonicalTitle())
                    .difficulty(item.getProblem().getDifficulty() != null ? item.getProblem().getDifficulty().name() : null)
                    .estimatedMinutes(item.getEstimatedMinutes())
                    .patterns(patternNames)
                    .followUpType(item.getFollowUpType())
                    .followUpDetail(item.getFollowUpDetail())
                    .reason(item.getReasonSummary())
                    .valueBreakdown(breakdown)
                    .completed(item.getCompleted())
                    .completedAt(item.getCompletedAt())
                    .skipped(item.getSkipped())
                    .skippedReason(item.getSkippedReason())
                    .build();

            sectionMap.computeIfAbsent(item.getItemType(), k -> new ArrayList<>()).add(dto);
        }

        List<PlanSectionDto> sections = sectionMap.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> PlanSectionDto.builder().type(e.getKey()).items(e.getValue()).build())
                .toList();

        int overdueTotal = plan.getOverdueTotal() != null ? plan.getOverdueTotal() : 0;
        int overdueShown = plan.getOverdueShown() != null ? plan.getOverdueShown() : 0;
        String overdueMessage = overdueTotal > 0
                ? String.format("%d overdue revisions total. %d most urgent scheduled today.", overdueTotal, overdueShown)
                : "No overdue revisions.";

        OverdueSummaryDto overdueSummary = OverdueSummaryDto.builder()
                .totalOverdue(overdueTotal)
                .showingToday(overdueShown)
                .message(overdueMessage)
                .build();

        return DailyPlanResponse.builder()
                .planId(plan.getId())
                .date(plan.getPlanDate())
                .totalEstimatedMinutes(plan.getTotalEstimatedMin())
                .dailyBudgetMinutes(plan.getBudgetMinutes())
                .completedMinutes(completedMinutes)
                .completedCount(completedCount)
                .totalCount(items.size())
                .overdueSummary(overdueSummary)
                .sections(sections)
                .build();
    }
}
