package com.preptrack.company.entity;

import com.preptrack.pattern.entity.Pattern;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_pattern_weights", uniqueConstraints = @UniqueConstraint(columnNames = {"company_profile_id", "pattern_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CompanyPatternWeight {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_profile_id", nullable = false)
    private CompanyProfile companyProfile;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pattern_id", nullable = false)
    private Pattern pattern;
    @Column(nullable = false)
    private Double weight;
}
