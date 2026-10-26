package com.preptrack.revision.entity;

import com.preptrack.attempt.entity.AttemptOutcome;
import com.preptrack.auth.entity.User;
import com.preptrack.problem.entity.Problem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "revision_cards", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "problem_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "next_review_date", nullable = false)
    private LocalDate nextReviewDate;

    @Column(name = "review_count")
    @Builder.Default
    private Integer reviewCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_outcome", length = 30)
    private AttemptOutcome lastOutcome;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_follow_up", length = 30)
    private FollowUpType requiredFollowUp;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
