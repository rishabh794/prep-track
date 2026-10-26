package com.preptrack.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPlanResponse {
    private Long planId;
    private LocalDate date;
    private Integer totalEstimatedMinutes;
    private Integer dailyBudgetMinutes;
    private Integer completedMinutes;
    private Integer completedCount;
    private Integer totalCount;
    private OverdueSummaryDto overdueSummary;
    @Builder.Default
    private List<PlanSectionDto> sections = new ArrayList<>();
}
