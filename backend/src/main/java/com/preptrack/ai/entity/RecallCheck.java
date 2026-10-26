package com.preptrack.ai.entity;

import com.preptrack.revision.entity.RevisionCard;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "recall_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecallCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_card_id", nullable = false)
    private RevisionCard revisionCard;

    @Column(name = "user_approach", nullable = false, columnDefinition = "TEXT")
    private String userApproach;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_assessment", length = 10)
    private RecallGrade aiAssessment;

    @Column(name = "ai_reasoning", columnDefinition = "TEXT")
    private String aiReasoning;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_override", length = 10)
    private RecallGrade userOverride;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_grade", nullable = false, length = 10)
    private RecallGrade finalGrade;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
