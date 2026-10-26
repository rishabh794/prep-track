package com.preptrack.pattern.entity;

import com.preptrack.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_pattern_state", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "pattern_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPatternState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pattern_id", nullable = false)
    private Pattern pattern;

    @Column(nullable = false)
    @Builder.Default
    private Double skill = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Double stability = 1.0;

    @Column(name = "last_practiced_at")
    private LocalDateTime lastPracticedAt; // NULL = never practiced

    @Column(name = "total_attempts")
    @Builder.Default
    private Integer totalAttempts = 0;

    @Column(name = "independent_solves")
    @Builder.Default
    private Integer independentSolves = 0;

    @Column(name = "interview_boost")
    @Builder.Default
    private Double interviewBoost = 0.0;

    @Column(name = "interview_boost_set_at")
    private LocalDateTime interviewBoostSetAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
