package com.preptrack.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(name = "daily_time_budget_min", nullable = false)
    private Integer dailyTimeBudgetMin = 90;

    @Builder.Default
    @Column(name = "max_new_problems_day", nullable = false)
    private Integer maxNewProblemsDay = 3;

    @Builder.Default
    @Column(name = "max_revisions_day", nullable = false)
    private Integer maxRevisionsDay = 10;

    @Builder.Default
    @Column(name = "target_skill_level")
    private Double targetSkillLevel = 0.8;

    @Builder.Default
    @Column(name = "onboarding_complete")
    private Boolean onboardingComplete = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
