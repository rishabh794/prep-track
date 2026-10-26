package com.preptrack.planner.engine;

import com.preptrack.planner.dto.ValueBreakdown;
import com.preptrack.planner.entity.PlanItemType;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.entity.FollowUpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanCandidate {
    private Problem problem;
    private PlanItemType type;
    private int timeMinutes;
    private double value;
    private ValueBreakdown valueBreakdown;
    private FollowUpType followUpType;
    private String followUpDetail;
    private Long primaryPatternId;
    private Long interviewDays;
    private String interviewCompany;
}
