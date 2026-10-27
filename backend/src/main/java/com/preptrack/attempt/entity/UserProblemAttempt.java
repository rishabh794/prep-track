package com.preptrack.attempt.entity;

import com.preptrack.auth.entity.User;
import com.preptrack.problem.entity.Problem;
import com.preptrack.revision.entity.FollowUpType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_problem_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProblemAttempt {

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
    @Column(nullable = false, length = 30)
    private AttemptOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private FailureReason failureReason;

    @Column(name = "time_taken_sec")
    private Integer timeTakenSec;

    @Column(name = "hints_used")
    @Builder.Default
    private Integer hintsUsed = 0;

    @Column(name = "is_revision")
    @Builder.Default
    private Boolean isRevision = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    @Builder.Default
    private AttemptActivityType activityType = AttemptActivityType.CODING_ATTEMPT;

    @Column(name = "recall_check_id")
    private Long recallCheckId;

    @Enumerated(EnumType.STRING)
    @Column(name = "follow_up_type", length = 30)
    private FollowUpType followUpType;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "attempted_at", updatable = false)
    private LocalDateTime attemptedAt;
}
