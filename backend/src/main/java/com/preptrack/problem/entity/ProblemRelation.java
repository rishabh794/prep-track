package com.preptrack.problem.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_relations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"source_problem_id", "target_problem_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_problem_id", nullable = false)
    private Problem sourceProblem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_problem_id", nullable = false)
    private Problem targetProblem;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 25)
    private RelationType relationType;

    @Builder.Default
    @Column(nullable = false)
    private Boolean reviewed = false;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
