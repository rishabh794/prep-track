package com.preptrack.planner.entity;

import com.preptrack.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "daily_plans", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "plan_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "budget_minutes", nullable = false)
    private Integer budgetMinutes;

    @Column(name = "total_estimated_min", nullable = false)
    private Integer totalEstimatedMin;

    @Column(name = "total_value", nullable = false)
    private Double totalValue;

    @Column(name = "overdue_total")
    @Builder.Default
    private Integer overdueTotal = 0;

    @Column(name = "overdue_shown")
    @Builder.Default
    private Integer overdueShown = 0;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;

    @OneToMany(mappedBy = "dailyPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<DailyPlanItem> items = new ArrayList<>();
}
