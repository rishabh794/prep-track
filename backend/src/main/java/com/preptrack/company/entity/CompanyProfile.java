package com.preptrack.company.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_profiles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CompanyProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String slug;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 30)
    private String provenance;
    @Column(name = "source_version", nullable = false, length = 100)
    private String sourceVersion;
    @Column(name = "source_notes", nullable = false, columnDefinition = "TEXT")
    private String sourceNotes;
    @Column(name = "seed_checksum", nullable = false, length = 64)
    private String seedChecksum;
}
