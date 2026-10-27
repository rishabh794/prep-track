package com.preptrack.application.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "interview_date", nullable = false)
    private LocalDate interviewDate;

    @Column(name = "round_name", length = 100)
    private String roundName;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "raw_feedback", columnDefinition = "TEXT")
    private String rawFeedback;

    @Builder.Default
    @Column(name = "ai_extracted")
    private Boolean aiExtracted = false;

    @Column(name = "topics_confirmed_at")
    private LocalDateTime topicsConfirmedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(length = 20)
    private InterviewOutcome outcome = InterviewOutcome.UPCOMING;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InterviewTopic> topics = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
