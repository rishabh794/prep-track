package com.preptrack.application.entity;

import com.preptrack.pattern.entity.Pattern;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_topics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pattern_id", nullable = false)
    private Pattern pattern;

    @Builder.Default
    @Column(name = "was_weak")
    private Boolean wasWeak = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean confirmed = false;
}
