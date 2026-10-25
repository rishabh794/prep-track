package com.preptrack.problem.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_sources", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform", "external_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Platform platform;

    @Column(name = "external_id", length = 50)
    private String externalId;

    @Column(name = "external_url", nullable = false, length = 500)
    private String externalUrl;

    @Column(name = "external_title")
    private String externalTitle;
}
