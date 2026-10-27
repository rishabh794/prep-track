package com.preptrack.seed.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "catalog_seed_versions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CatalogSeedVersion {
    @Id @Column(length = 100) private String version;
    @Column(nullable = false, length = 64) private String checksum;
    @Column(name = "applied_at", nullable = false) private Instant appliedAt;
}
