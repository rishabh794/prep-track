package com.preptrack.planner.entity;

import com.preptrack.attempt.entity.UserProblemAttempt;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.entity.FollowUpType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "daily_plan_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private DailyPlan dailyPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 15)
    private PlanItemType itemType;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "estimated_minutes", nullable = false)
    private Integer estimatedMinutes;

    @Column(name = "value_score", nullable = false)
    private Double valueScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_json", columnDefinition = "jsonb")
    private String valueJson;

    @Column(name = "reason_summary", nullable = false, columnDefinition = "TEXT")
    private String reasonSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "follow_up_type", length = 30)
    private FollowUpType followUpType;

    @Column(name = "follow_up_detail", columnDefinition = "TEXT")
    private String followUpDetail;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private UserProblemAttempt attempt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean skipped = false;

    @Column(name = "skipped_reason", columnDefinition = "TEXT")
    private String skippedReason;
}
