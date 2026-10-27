package com.preptrack.company.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_aliases")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CompanyAlias {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_profile_id", nullable = false)
    private CompanyProfile companyProfile;
    @Column(nullable = false, unique = true, length = 150)
    private String alias;
}
