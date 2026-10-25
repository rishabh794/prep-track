package com.preptrack.pattern.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "patterns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "parent_topic", length = 100)
    private String parentTopic;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "difficulty_tier")
    private Integer difficultyTier = 1;
}
