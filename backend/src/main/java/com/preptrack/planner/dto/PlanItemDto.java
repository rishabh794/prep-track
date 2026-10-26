package com.preptrack.planner.dto;

import com.preptrack.revision.entity.FollowUpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanItemDto {
    private Long planItemId;
    private Long problemId;
    private String title;
    private String difficulty;
    private Integer estimatedMinutes;
    private List<String> patterns;
    private FollowUpType followUpType;
    private String followUpDetail;
    private String reason;
    private ValueBreakdown valueBreakdown;
    private Boolean completed;
    private LocalDateTime completedAt;
    private Boolean skipped;
    private String skippedReason;
}
