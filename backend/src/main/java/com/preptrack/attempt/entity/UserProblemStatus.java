package com.preptrack.attempt.entity;

import com.preptrack.auth.entity.User;
import com.preptrack.problem.entity.Problem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_problem_status", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "problem_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProblemStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProblemStatus status = ProblemStatus.UNSOLVED;

    @Column(name = "total_attempts")
    @Builder.Default
    private Integer totalAttempts = 0;

    @Column(name = "independent_solves")
    @Builder.Default
    private Integer independentSolves = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_outcome", length = 30)
    private AttemptOutcome lastOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failure_reason", length = 30)
    private FailureReason lastFailureReason;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "first_solved_at")
    private LocalDateTime firstSolvedAt;

    @Column(name = "insight_note", columnDefinition = "TEXT")
    private String insightNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
